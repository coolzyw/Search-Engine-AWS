import subprocess, os, shutil, time, pprint, random

from elasticsearch import Elasticsearch, RequestsHttpConnection
from requests_aws4auth import AWS4Auth

pp = pprint.PrettyPrinter(indent=4, width=100)
random.seed()

JAR_FILENAME = 'news-import-1.0-SNAPSHOT.jar'

# murphy.wot.eecs.northwestern.edu config:
JAVA_HOME = '/usr/lib/jvm/jre-1.8.0-openjdk.x86_64/'
MVN = '/home/yzh1927/cs396/apache-maven-3.6.2/bin/mvn'

# typical mac configuration:
#JAVA_HOME = '/Applications/IntelliJ IDEA CE.app/Contents/jbr/Contents/Home'
#MVN = '/usr/local/bin/mvn'

# typical Windows configuration:
#JAVA_HOME = "C:\\Program Files\\Java\\jdk-13"
#MVN = "C:\\Program Files\\apache-maven-3.6.2\\bin\\mvn.cmd"

AWS_ACCESS_KEY_ID = 'AKIA3ATZL6C6Q5MJYRZF'
AWS_SECRET_ACCESS_KEY = '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'
ELASTIC_SEARCH_HOST = 'search-news-crawl-665pfxjl7sqim2qf4yfd76rfly.us-east-2.es.amazonaws.com'
ELASTIC_SEARCH_INDEX = 'test-index-%d' % random.randrange(999999999)
COMMON_CRAWL_FILENAME = 'crawl-data/CC-NEWS/2019/09/CC-NEWS-20190901022141-01066.warc.gz'

awsauth = AWS4Auth(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, 'us-east-2', 'es')
es = Elasticsearch(
    hosts=[{'host': ELASTIC_SEARCH_HOST, 'port': 443}],
    http_auth=awsauth,
    use_ssl=True,
    verify_certs=True,
    connection_class=RequestsHttpConnection
)

def main():
    elasticsearch_delete_index()

    success = run_jar(common_crawl_filename=None)
    my_assert_equal("java code exec success without COMMON_CRAWL_FILENAME set", True, success, 10)
    index_filled = elasticsearch_index_size() > 0
    my_assert_equal("nonzero index size", True, index_filled, 5)
    my_assert_equal("getting latest and not CC-NEWS-20190901022141-01066", True,
                    index_filled and len(elasticsearch_query(["Evanston", ""]))==0, 5)
    elasticsearch_delete_index()

    start = time.perf_counter()
    success = run_jar(JAR_FILENAME)
    elapsed = int(1000*(time.perf_counter()-start))/1000.0
    print('elapsed seconds: %d' % elapsed)
    my_assert_equal("java code exec success", True, success, 20)
    my_assert_equal("java code finished in under 40 minutes", True,
                    success and (elapsed < 40 * 60), 10)

    r = elasticsearch_query(["Evanston"])
    pp.pprint(r)
    print("results returned by query: ", len(r))
    my_assert_equal("two search results for 'Evanston'", 2, len(r), 10)

    # look for text of vanished page https://yourvalley.net/yourvalley/sports/minnesota-twins-break-mlb-single-season-hr-mark-with-268/
    r = elasticsearch_query("Minnesota Twins break MLB single-season HR mark 268 Valley Saturday Peoria dog park Litchfield Glendale Boswell".split(" "))
    pp.pprint(r)
    print("results returned by query: ", len(r))
    my_assert_equal("", 1, len(r), 10)

    # check that size of index is expected
    sz = elasticsearch_index_size()
    print("size of index is: %d" % sz)
    my_assert_equal(">30k results", True, sz > 30000, 10)

    elasticsearch_delete_index()


def run_jar(jar_filename=JAR_FILENAME, common_crawl_filename=COMMON_CRAWL_FILENAME):
    tmp_dir = "__tester_tmp"
    shutil.rmtree(tmp_dir)
    os.makedirs(tmp_dir, exist_ok=True)
    os.chdir(tmp_dir)
    subprocess.run(['jar', 'xvf', '../'+jar_filename])
    shutil.move('META-INF/maven/edu.northwestern.ssa/news-import/pom.xml', 'pom.xml')
    shutil.rmtree('META-INF')
    os.makedirs('src/main/java', exist_ok=True)
    shutil.move('edu', 'src/main/java')
    print("Maven is about to run.  This may take a while the first time...")
    completed_proc = subprocess.run([MVN, 'compile'])
    my_assert_equal("java code compile return code", 0, completed_proc.returncode, 30)
    env = {
         'JAVA_HOME' : JAVA_HOME,
         'AWS_ACCESS_KEY_ID': AWS_ACCESS_KEY_ID,
         'AWS_SECRET_ACCESS_KEY': AWS_SECRET_ACCESS_KEY,
         'ELASTIC_SEARCH_INDEX': ELASTIC_SEARCH_INDEX,
         'ELASTIC_SEARCH_HOST': ELASTIC_SEARCH_HOST
    }
    if common_crawl_filename is not None:
        env['COMMON_CRAWL_FILENAME'] = common_crawl_filename

    completed_proc = subprocess.run([MVN, 'exec:java', '-Dexec.mainClass=edu.northwestern.ssa.App'], env=env)
    os.chdir('..')
    return completed_proc.returncode == 0


# adapted from:
# https://docs.aws.amazon.com/elasticsearch-service/latest/developerguide/es-request-signing.html#es-request-signing-python
# https://elasticsearch-py.readthedocs.io/en/master/api.html#elasticsearch
def elasticsearch_query(keywords, index=ELASTIC_SEARCH_INDEX):
    r = es.search(index=index, ignore=[400, 404], body={
        'query': {
            'bool': {
                'must': [{'match': {'txt': k}} for k in keywords]
            }
        },
        'highlight': {
            'fields': {
                'txt': {}
            }
        }
    })
    if 'error' in r: return []
    return [{
                'url': x['_source']['url'],
                'title':
                    x['_source']['title'] if 'title' in x['_source'] else None,
                'matching_text': x['highlight']['txt']
            } for x in r['hits']['hits']]


def elasticsearch_index_size(index=ELASTIC_SEARCH_INDEX):
    idx_info = es.indices.stats(index, ignore=[400, 404])
    if 'error' in idx_info: return 0
    return idx_info['indices'][index]['total']['docs']['count']


def elasticsearch_delete_index(index=ELASTIC_SEARCH_INDEX):
    es.indices.delete(index, ignore=[400, 404])


def my_assert_reached(description, points):
    print("TEST PASSED: %s, %d/%d points awarded" % (description, points, points))


def my_assert_equal(description, expected, actual, points):
    success = (expected == actual)
    print("TEST %s: %s, expected value:%s, actual value:%s\n\t%d/%d points awarded" %
          ("PASSED" if success else "FAILED", description, expected, actual, points if success else 0, points))

if __name__ == '__main__':
    main()

package edu.northwestern.ssa;
// import com.sun.org.apache.xerces.internal.xs.datatypes.ObjectList;
import org.archive.io.ArchiveRecord;
import org.archive.io.ArchiveRecordHeader;
import org.json.JSONObject;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.awt.image.ImageObserver;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;

import org.archive.io.warc.WARCReaderFactory;
import org.archive.io.ArchiveReader;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import javax.swing.text.TabExpander;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

public class App implements RequestHandler<Map<String,Object>, String>{

    public String handleRequest(Map<String,Object> input, Context context) {
        LambdaLogger logger = context.getLogger();
        logger.log("received : " + input);
        return "done";
    }

    public static void main(String[] args) throws IOException {
        // PART 1
        String hostname = System.getenv("ELASTIC_SEARCH_HOST");
        String index = System.getenv("ELASTIC_SEARCH_INDEX");
        // tolerate common_crawlfile name to be missing
        String commonCrawl = System.getenv("COMMON_CRAWL_FILENAME");
        String bucket = "commoncrawl";
        String key = "crawl-data/CC-NEWS/2019/09/CC-NEWS-20190901022141-01066.warc.gz";
        S3Client s3 = S3Client.builder()
                .region(Region.US_EAST_1)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofMinutes(30)).build())
                .build();
        System.out.println("build success");
        File f = new File("/Users/yiweizhang/Desktop/comp_sci 396/project/ssa-skeleton/output.warc.gz");
        if (!f.exists()) {
            if (commonCrawl == null) {
                ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix("crawl-data/CC-NEWS/2019/")
                        .build();
                ListObjectsV2Iterable listRes = s3.listObjectsV2Paginator(listReq);
                Instant latest = null;
                String latestKey = null;
                for (S3Object content : listRes.contents()) {
                    System.out.println(" Key: " + content.key() + " size = " + content.size());
                    Instant current = content.lastModified();
                    if (latest == null || current.compareTo(latest) > 0) {
                        latest = current;
                        latestKey = content.key();
                    }
                }
                System.out.println(latestKey);
                s3.getObject(GetObjectRequest.builder().bucket(bucket).key(latestKey).build(), ResponseTransformer.toFile(f));
                s3.close();
            }
            else {
                s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(), ResponseTransformer.toFile(f));
                s3.close();
            }
        }

        System.out.println("Finish Part 1");
        // PART 2 3 4
        // create index
        Elasticsearch es = new Elasticsearch("es");
        es.deleteIndex(hostname, index);
        HttpExecuteResponse r1 = es.createIndex(hostname, index);
        ArchiveReader reader = WARCReaderFactory.get(f);
        Iterator<ArchiveRecord> it = reader.iterator();
        int count = 0;
        ExecutorService executor = Executors.newFixedThreadPool(50);
        while (it.hasNext()) {
            ArchiveRecord next = it.next();
            ArchiveRecordHeader h = next.getHeader();
            // if not response, just ignore
            if (!h.getHeaderValue("WARC-Type").equals("response")) {
                continue;
            }
            String URL = h.getUrl();
            try {
                // next.available
                // Create a byte array that is as long as all the record's stated length
                int size = next.available();
                byte[] charArray = new byte[size];
                int offset = 0;
                int numRead = 0;
                while (numRead >= 0) {
                    numRead = next.read(charArray, offset, charArray.length);
                    offset += numRead;
                }
                String content = new String(charArray);
                int end = content.indexOf("\r\n\r\n");
                if (end != - 1) {
                    if (count % 100 == 0) {
                        System.out.println("count " + count);
                    }
                    count++;
                    Runnable runnableTask = () -> {
                        try {
                            String htmlString = content.substring(end + 4, content.length());
                            Document doc = Jsoup.parse(htmlString);
                            String title = doc.title();
                            String text = doc.text();
                            HttpExecuteResponse r2 = es.putDoc(title, URL, text, hostname, index);
                            r2.responseBody().get().close();
                        } catch (Exception e) {
                            System.out.println("invalid testcase");
                        }
                    };
                    executor.execute(runnableTask);
                }
            }
            catch (OutOfMemoryError e) {
                System.out.println("heap out of space");
            }
        }
        r1.responseBody().get().close();
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            System.out.println("interruption exception");
        }
        finally {
            System.exit(0);
        }
    }
}
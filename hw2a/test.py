# testing script for hw2a
# make sure your server is running on the java side
# also, requires python3 with requests library installed
# install requests at command line with pip install requests

from difflib import SequenceMatcher
from time import sleep
import requests


class ESTester(object):
    def __init__(self):
        self.testRoot = "http://localhost:8080/api/search"
        self.truthRoot = "http://ssa-hw2-backend.stevetarzia.com/api/search"
        self.queryStrings = [
            "?query=Donald%20Trump",
            "?query=Donald%20Trump&count=2",
            "?query=Donald%20Trump&count=2&offset=3",
            "?query=Donald%20Trump&date=2019-10-17",
            "?query=Donald%20Trump&date=2019-10-17&count=5",
            "?query=Donald%20Trump&date=2019-10-18&language=de",
            "?query=Donald%20Trump%20Nancy%20Pelosi%20Bernie%20Sanders",
            "?query=Donald%20Trump%20Nancy%20Pelosi%20Bernie%20Sanders&count=2",
            "?query=Donald%20Trump%20Nancy%20Pelosi%20Bernie%20Sanders&count=2&offset=3",
            "?query=Donald%20Trump%20Nancy%20Pelosi%20Bernie%20Sanders&date=2019-10-17",
            "?query=Donald%20Trump%20Nancy%20Pelosi%20Bernie%20Sanders&date=2019-10-17&count=5",
            "?query=Donald%20Trump%20Nancy%20Pelosi%20Bernie%20Sanders&date=2019-10-18&language=es"
        ]
        self.runTests()

    def runTests(self):
        print("Running tests...")
        self.checkFailureCases()
        for query in self.queryStrings:
            print("\n-------\nChecking with query %s ..." % query)
            testResponse = requests.get(self.testRoot+query)
            truthResponse = requests.get(self.truthRoot+query)
            testJson = testResponse.json()
            truthJson = truthResponse.json()
            errorFlag = False
            if testJson["returned_results"] != truthJson["returned_results"]:
                errorFlag = True
                print(" => ERROR: returned_results should have been %s but got %s" % (truthJson["returned_results"], testJson["returned_results"]))
            if testJson["total_results"] != truthJson["total_results"]:
                errorFlag = True
                print(" => ERROR: total_results should have been %s but got %s" % (truthJson["total_results"], testJson["total_results"]))
            if not self.equalCheck(truthJson["articles"], testJson["articles"]):
                # the call above should print out any error messages...
                errorFlag = True
            if not errorFlag:
                print("The result match.\n-------")
                sleep(.5)

    def checkFailureCases(self):
        print("Checking to make sure server returns 400 errors when no query present...")
        errorFlag = False
        for query in ["", "?count=5", "?date=2019-10-18"]:
            results = requests.get(self.testRoot+query)
            if results.status_code != 400:
                errorFlag = True
                print(" => ERROR: 400 code should have been returned with query %s, but got status %s" % (query, results.status_code))
        if not errorFlag:
            print("400 tests passed.")

    def equalCheck(self, truth, test):
        sameLength = len(test) == len(truth)
        zippedPairs = zip(truth, test)
        mismatch = False
        counter = 0
        mainKeys = ["title", "txt", "url"]
        for trth, tst in zippedPairs:
            if trth != tst:
                # check to make sure it's not because of mismatch date/lang keys weirdness
                mainMismatches = [k for k in mainKeys if tst[k] != trth[k]]
                if not mainMismatches:
                    # mostly right...check for date/lang thing...
                    dateSame = ("date" in trth and not trth["date"] and "date" not in tst) or (trth["date"] == tst["date"])
                    langSame = ("lang" in trth and not trth["lang"] and "lang" not in tst) or (trth["lang"] == tst["lang"])
                    if not (dateSame and langSame):
                        # okay, actually mismatch on date/lang...
                        mismatch = True
                        print(" => ERROR: everything matched except something's wrong with date or lang keys in this result with url: " % trth["url"])
                else:
                    # this is definitely wrong...
                    mismatch = True
                    print(" => ERROR: the following keys don't match for article with index %i : %s" % (counter, ", ".join(mainMismatches)))
                    # breakpoint()
                    #... check the similarity at least...
                    print(" =====> Quick double check to see if the articles are substantially the same: the similarity between truth and test txt values is %s" % SequenceMatcher(None, trth["txt"], tst["txt"]).ratio())
            counter += 1

        return not mismatch

if __name__ == '__main__':
    ESTester()
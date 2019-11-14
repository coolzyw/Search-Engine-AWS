package lambdatest;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveRecord;
import org.archive.io.ArchiveRecordHeader;
import org.archive.io.warc.WARCReaderFactory;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import sun.jvm.hotspot.debugger.arm.ARMThreadContext;

import java.io.InputStream;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Hello implements RequestHandler<Map<String,Object>, String>{
    public String handleRequest(Map<String,Object> input, Context context) {
        S3Client s3 = S3Client.builder()
                .region(Region.US_EAST_1)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofMinutes(30)).build())
                .build();

        ListObjectsV2Request keyRequest = ListObjectsV2Request.builder()
                .bucket("commoncrawl")
                .encodingType("url")
                .prefix("crawl-data/CC-NEWS/")
                .build();

        ListObjectsV2Iterable keyResponse = s3.listObjectsV2Paginator(keyRequest);

        Instant newest = Instant.MIN;
        String fileName = "";
        System.out.println("finish the request");

        for (ListObjectsV2Response page : keyResponse) {
            List<S3Object> files_on_page = page.contents();
            for (S3Object object : files_on_page) {
                String key = object.key();
                if (key.endsWith(".warc.gz")) {
                    Instant i = object.lastModified();
                    if (i.compareTo(newest) == 1) {
                        newest = i;
                        fileName = key;
                    }
                }
            }
        }

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket("commoncrawl")
                .key(fileName)
                .build();

        InputStream is = s3.getObject(request, ResponseTransformer.toInputStream());
        ArchiveReader ar = null;
        try {
            ar = WARCReaderFactory.get(fileName, is, true);
        }
        catch (Exception e) {
            System.out.println("warc file read error");
        }
        System.out.print("parsing the file finish");

        Iterator<ArchiveRecord> it = ar.iterator();
        while (it.hasNext()) {
            ArchiveRecord next = it.next();
            ArchiveRecordHeader h = next.getHeader();
            // if not response, just ignore
            if (!h.getHeaderValue("WARC-Type").equals("response")) {
                continue;
            }
            String URL = h.getUrl();
            context.getLogger().log(URL);
        }


        s3.close();
        return fileName;
    }


}
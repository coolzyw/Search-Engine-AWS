package lambdatest;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.commons.io.IOUtils;
import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveRecord;
import org.archive.io.ArchiveRecordHeader;
import org.archive.io.warc.WARCReaderFactory;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.io.*;
import java.net.URI;
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
        LambdaLogger logger = context.getLogger();

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

        // get the latest name in S3 bucket
        String latestBucket = "yzh1927-bucket";
        String key = "bucket.txt";
        GetObjectRequest latestRequest = GetObjectRequest.builder().
                bucket(latestBucket).
                key(key).
                build();
        InputStream latestInputStream = s3.getObject(latestRequest, ResponseTransformer.toInputStream());
        String latestFileName = null;
        BufferedReader reader = new BufferedReader(new InputStreamReader(latestInputStream));
        try {
            latestFileName = reader.readLine();
        }
        catch (Exception e) {
            System.out.print("bucket read error");
        }

        if (latestFileName != null && latestFileName.equals(fileName)) {
            context.getLogger().log("filename is empty/same, no need to update bucket");
            return fileName;
        }
        else {
            // first delete the object in the S3 bucket
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder().bucket(latestBucket).key(key).build();
            s3.deleteObject(deleteRequest);
            // then upload a new object to the S3 bucket
            PutObjectRequest putRequest = PutObjectRequest.builder().bucket(latestBucket).key(key).build();
            s3.putObject(putRequest, RequestBody.fromString(fileName));
            context.getLogger().log("updated S3 bucket");
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

        SqsClient sqs = SqsClient.builder()
                .region(Region.US_EAST_1)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofMinutes(30)).build())
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(50);
        String sqsUrl = "https://sqs.us-east-1.amazonaws.com/757242523837/sqs-queue-yzh1927";

        Iterator<ArchiveRecord> it = ar.iterator();
        while (it.hasNext()) {
            ArchiveRecord next = it.next();
            ArchiveRecordHeader h = next.getHeader();
            // if not response, just ignore
            if (!h.getHeaderValue("WARC-Type").equals("response")) {
                continue;
            }
            String url = h.getUrl();
            try {
                next.read();
                byte[] data = IOUtils.toByteArray(next, next.available());
                String fullText = new String(data);

                int bodyStart = 4 + fullText.indexOf("\r\n\r\n");
                String bodyText = fullText.substring(bodyStart);
                Document doc = Jsoup.parse(bodyText);

                String title = doc.title();
                String text = doc.text();

                if (title.isEmpty()) continue;

                String jsonString = new JSONObject()
                        .put("url", url)
                        .put("title", title)
                        .put("text", text)
                        .toString();

                logger.log(jsonString);

                Runnable enqueueEntry = () -> {
                    sqs.sendMessage(SendMessageRequest.builder()
                            .queueUrl(sqsUrl)
                            .messageBody(jsonString)
                            .build());
                };

                executor.execute(enqueueEntry);
            }
            catch (IOException i){
                continue;
            }
        }

        executor.shutdown();
        try {
            executor.awaitTermination(120, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        s3.close();
        sqs.close();

        return fileName;
    }


}
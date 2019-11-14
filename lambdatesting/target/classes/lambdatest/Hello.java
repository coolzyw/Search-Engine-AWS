package lambdatest;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class Hello implements RequestHandler<Map<String,Object>, String>{
    public String handleRequest(Map<String,Object> input, Context context) {
        LambdaLogger logger = context.getLogger();
        logger.log("received : " + "the new thing!!");

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

        s3.close();

        return fileName;
    }


}
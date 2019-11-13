package edu.northwestern.ssa;

import org.json.JSONObject;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.*;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.utils.StringInputStream;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/** based on sample code at:
 * https://docs.aws.amazon.com/elasticsearch-service/latest/developerguide/es-request-signing.html
 * https://github.com/aws/aws-sdk-java-v2/issues/339
 */
public class AwsSignedRestRequest implements Closeable {
    private Aws4SignerParams params;
    private Aws4Signer signer = Aws4Signer.create();
    private SdkHttpClient httpClient = ApacheHttpClient.builder().build();

    /** @param serviceName would be "es" for Elasticsearch */
    public AwsSignedRestRequest(String serviceName) {
        params = Aws4SignerParams.builder()
                .awsCredentials(AwsBasicCredentials.create(
                        Config.getParam("AWS_ACCESS_KEY_ID"),
                        Config.getParam("AWS_SECRET_ACCESS_KEY")))
                .signingName(serviceName)
                .signingRegion(Region.US_EAST_2)
                .build();
    }

    /** @param path should not have a leading "/" */
    public HttpExecuteResponse restRequest(SdkHttpMethod method, String host, String path,
                                              Optional<Map<String, String>> queryParameters)
            throws IOException {
        return restRequest(method, host, path, queryParameters, Optional.empty());
    }

    public HttpExecuteResponse restRequest(SdkHttpMethod method, String host, String path,
                                              Optional<Map<String, String>> queryParameters,
                                              Optional<JSONObject> body)
            throws IOException {
        if (method.equals(SdkHttpMethod.GET) && body.isPresent()) {
            throw new IOException("GET request cannot have a body. Otherwise Aws4Signer will include the body in the " +
                    "signature calculation, but it will not be included in the request, leading to a 403 error back from AWS.");
        }
        SdkHttpFullRequest.Builder b = SdkHttpFullRequest.builder()
                .encodedPath(path)
                .host(host)
                .method(method)
                .protocol("https");
        body.ifPresent(realBody -> {
            b.putHeader("Content-Type", "application/json; charset=utf-8");
            b.contentStreamProvider(() -> new StringInputStream(realBody.toString()));
        });
        queryParameters.ifPresent(qp -> {
            qp.forEach((key, value) -> b.putRawQueryParameter(key, value));
        });
        SdkHttpFullRequest request = b.build();

        // now sign it
        SdkHttpFullRequest signedRequest = signer.sign(request, params);
        HttpExecuteRequest.Builder rb = HttpExecuteRequest.builder().request(signedRequest);
        // !!!: line below is necessary even though the contentStreamProvider is in the request.
        // Otherwise the body will be missing from the request and auth signature will fail.
        request.contentStreamProvider().ifPresent(c -> rb.contentStreamProvider(c));
        return httpClient.prepareRequest(rb.build()).call();
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }
}

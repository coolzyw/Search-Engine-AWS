package edu.northwestern.ssa;

import org.json.JSONObject;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpMethod;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.http.SdkHttpMethod;

public class Elasticsearch extends AwsSignedRestRequest {
    public Elasticsearch(String serviceName) {
        super(serviceName);
    }

    public HttpExecuteResponse createIndex(String host, String path) throws IOException {
        return super.restRequest(SdkHttpMethod.PUT, host, path, Optional.empty(), Optional.empty());
    }

    public HttpExecuteResponse putDoc(String title, String URL, String text, String host, String path) throws IOException{
        JSONObject js = new JSONObject();
        js.put("title", title);
        js.put("url", URL);
        js.put("txt", text);
        path += "/_doc";
        return super.restRequest(SdkHttpMethod.POST, host, path, Optional.empty(), Optional.of(js));
    }

    public HttpExecuteResponse deleteIndex(String host, String path) throws IOException {
        return super.restRequest(SdkHttpMethod.DELETE, host, path, Optional.empty(), Optional.empty());
    }
}

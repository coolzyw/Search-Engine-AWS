package lambdatest;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONObject;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpMethod;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class ElasticsearchRequest extends AwsSignedRestRequest {

    ElasticsearchRequest() {
        super("es");
    }

    protected HttpExecuteResponse CreateNewIndex() throws IOException {
        return restRequest(SdkHttpMethod.PUT, System.getenv("ELASTIC_SEARCH_HOST"), System.getenv("ELASTIC_SEARCH_INDEX"), Optional.empty(), Optional.empty());
    }

    protected HttpExecuteResponse PostNewDocument(String doctitle, String url, String plaintext) {
        JSONObject entry = new JSONObject();
        entry.put("title", doctitle);
        entry.put("url", url);
        entry.put("txt", plaintext);

        boolean overloaded = true;
        HttpExecuteResponse response = null;
        while (overloaded) {
            try {
                response = restRequest(SdkHttpMethod.POST, System.getenv("ELASTIC_SEARCH_HOST"), System.getenv("ELASTIC_SEARCH_INDEX") + "/_doc", Optional.empty(), java.util.Optional.ofNullable(entry));
                int statuscode = response.httpResponse().statusCode();
                //System.out.println(statuscode);
                if (statuscode == 201) {
                    overloaded = false;
                    //System.out.println("Post worked! I think");
                }
                response.responseBody().get().close();
            } catch (IOException io) {
                System.out.println("Overloaded... trying again. " + io.getMessage());
            }
        }
        return response;
    }
}

public class PostToElasticsearch implements RequestHandler<Map<String,Object>, String> {
    public String handleRequest(Map<String, Object> map, Context context) {

        ElasticsearchRequest es = new ElasticsearchRequest();
        ExecutorService executor = Executors.newFixedThreadPool(50);
        try {
            HttpExecuteResponse i = es.CreateNewIndex();
        } catch (IOException e) {
            e.printStackTrace();
        }
        LambdaLogger logger = context.getLogger();

        Object[] messageslist = (Object[]) map.get("Records");
        logger.log(Arrays.toString(messageslist) + ": THE THING!!");

        for (int i = 0; i < messageslist.length; i++) {
            Map<String, Object> message = (Map<String, Object>) messageslist[i];
            Map<String, String> fields = (Map<String, String>) message.get("body");
            String url = fields.get("url");
            logger.log(url);
            String title = fields.get("title");
            logger.log(title);
            String text = fields.get("text");
            logger.log(text);
            Runnable post_entry = () -> {
                try {
                    es.PostNewDocument(url, title, text);
                } catch (Exception something) {
                    System.out.println("Exception: " + something.getMessage());
                }
            };
            executor.execute(post_entry);
            logger.log("Did thing");
        }

        executor.shutdown();

        try {
            es.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "blep belp pelp";
    }
}

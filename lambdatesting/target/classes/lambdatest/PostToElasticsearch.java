package lambdatest;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.util.TextUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpMethod;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class ElasticsearchRequest extends AwsSignedRestRequest {

    ElasticsearchRequest() {
        super("es");
    }

    protected HttpExecuteResponse CreateNewIndex() throws IOException {
        return restRequest(SdkHttpMethod.PUT, System.getenv("ELASTIC_SEARCH_HOST"), System.getenv("ELASTIC_SEARCH_INDEX"), Optional.empty(), Optional.empty());
    }

    protected HttpExecuteResponse PostNewDocument(String doctitle, String url, String plaintext, String date, String lang) {
        JSONObject entry = new JSONObject();
        entry.put("title", doctitle);
        entry.put("url", url);
        entry.put("txt", plaintext);
        entry.put("lang", lang);
        entry.put("date", date);

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
        logger.log(map.toString());

        ArrayList<Object> messagesArray = (ArrayList<Object>) map.get("Records");
        Object[] messageslist = messagesArray.toArray();
        logger.log(Arrays.toString(messageslist) + ": THE THING!!");

        for (int i = 0; i < messageslist.length; i++) {
            Map<String, Object> message = (Map<String, Object>) messageslist[i];
            logger.log((String) message.get("body"));
            Map<String, String> fields = new HashMap<String, String>();
            ObjectMapper obj = new ObjectMapper();
            try {
                fields = obj.readValue((String) message.get("body"), new TypeReference<HashMap<String,String>>(){});
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            String url = (String) fields.get("url");
            logger.log(url);
            String title = (String) fields.get("title");
            logger.log(title);
            String text = (String) fields.get("text");
            logger.log(text);
            String date = (String) fields.get("date");
            logger.log(date);
            String lang = (String) fields.get("language");
            Runnable post_entry = () -> {
                try {
                    es.PostNewDocument(url, title, text, date, lang);
                } catch (Exception something) {
                    System.out.println("Exception: " + something.getMessage());
                }
            };
            executor.execute(post_entry);
            logger.log("Did thing");
        }

        executor.shutdown();
        try {
            executor.awaitTermination(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            es.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "blep belp pelp";
    }
}

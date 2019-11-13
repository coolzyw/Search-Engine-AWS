package edu.northwestern.ssa.api;

import edu.northwestern.ssa.AwsSignedRestRequest;
import jdk.nashorn.internal.parser.JSONParser;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpMethod;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;



@Path("/search")
public class Search {

    /** when testing, this is reachable at http://localhost:8080/api/search?query=hello */
    @GET
    public Response getMsg(@QueryParam("query") String q,
                           @QueryParam("language") String language,
                           @QueryParam("date") String date,
                           @DefaultValue ("10") @QueryParam("count") String count,
                           @QueryParam("offset") String offset) throws IOException {


        JSONObject results = new JSONObject();
        results.put("returned_results", 0);
        results.put("total_results", 0);
        results.put("articles", new JSONArray());

        if (q == null) {
            return Response.status(400).type("application/json").entity(results.toString(4))
                    .header("Access-Control-Allow-Origin", "*").build();
        }

        String hostname = getParam("ELASTIC_SEARCH_HOST");
        String index = getParam("ELASTIC_SEARCH_INDEX");
        String path = index + "/_search";

        StringBuilder sb = new StringBuilder();
        sb.append("txt:(");
        String[] queryComb = q.split(" ");
        for (int i = 0; i < queryComb.length; i++) {
            sb.append(queryComb[i]);
            if (i != queryComb.length - 1) {
                sb.append(" AND ");
            }
            else {
                sb.append(")");
            }
        }
        if (language != null) {
            sb.append(" AND ");
            sb.append("lang:" + language);
            System.out.println("language " + language);
        }
        if (date != null) {
            sb.append(" AND ");
            sb.append("date:");
            sb.append(date);
        }
        System.out.println("input query string " + q);

        String queryString = sb.toString();
        Map<String, String> query = new HashMap<>();
        query.put("q", queryString);
        if (offset != null) {
            query.put("from", offset);
        }
        query.put("size", count);
        query.put("track_total_hits", "true");

        AwsSignedRestRequest request = new AwsSignedRestRequest("es");
        System.out.println(hostname);
        System.out.println(path);
        System.out.println(queryString);

        HttpExecuteResponse response = request.restRequest(SdkHttpMethod.GET, hostname, path, Optional.of(query), Optional.empty());

        System.out.println("status code " + response.httpResponse().statusCode());
        AbortableInputStream in = response.responseBody().get();

        int c = 0;
        StringBuilder contextsb = new StringBuilder();
        while ((c = in.read()) != -1) {
            contextsb.append((char)c);
        }

        String contextRaw = contextsb.toString();
        String context = new String(contextRaw.getBytes("ISO-8859-1"),"UTF-8");
//        String context = new String(contextRaw.getBytes(), "UTF-8");
        JSONObject js = new JSONObject(context);
        if (!js.has("hits")) {
            results.put("returned_results", 0);
            results.put("total_results", 0);
            results.put("articles", new JSONArray());

            return Response.status(200).type("application/json").entity(results.toString(4))
                    // below header is for CORS
                    .header("Access-Control-Allow-Origin", "*").build();
        }

        JSONObject value = (JSONObject) js.get("hits");
        int total_result = (int) ((JSONObject) value.get("total")).get("value");
        JSONArray newJSONArray = new JSONArray();
        JSONArray inputJSONArray = (JSONArray) value.get("hits");
        int returned_result = inputJSONArray.length();
        for (int i = 0; i < inputJSONArray.length(); i++) {
            JSONObject eachResult = (JSONObject) inputJSONArray.get(i);
            JSONObject source = (JSONObject) eachResult.get("_source");
            JSONObject newObject = new JSONObject();
            if (source.has("title")) {
                String title = (String) source.get("title");
                newObject.put("title", title);
            }
            if (source.has("lang")) {
                String lang = (String) source.get("lang");
                newObject.put("lang", lang);
            }
            if (source.has("url")) {
                String url = (String) source.get("url");
                newObject.put("url", url);
            }
            if (source.has("date")) {
                String sourceDate = (String) source.get("date");
                newObject.put("date", sourceDate);
            }
            if (source.has("txt")) {
                String txt = (String) source.get("txt");
                newObject.put("txt", txt);
            }
            newJSONArray.put(newObject);
            System.out.println(eachResult);
        }

        results.put("returned_results", returned_result);
        results.put("total_results", total_result);
        results.put("articles", newJSONArray);

        return Response.status(200).type("application/json").entity(results.toString(4))
                // below header is for CORS
                .header("Access-Control-Allow-Origin", "*").build();
    }

    private static String getParam(String paramName) {
        String prop = System.getProperty(paramName);
        return (prop != null) ? prop : System.getenv(paramName);
    }
}

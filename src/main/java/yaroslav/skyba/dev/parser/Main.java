package yaroslav.skyba.dev.parser;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws URISyntaxException, IOException {
        final Main main = new Main();
        main.start();
    }

    public void start() throws URISyntaxException, IOException {
        final HttpGet httpGet1 = sendHttpGetRequest(1);
        final String response1 = getHttpGetResponse(httpGet1);
        final int pageCount = getPageCount(response1);
        final int productCount = getProductCount(response1);

        final List<Map<String, Object>> parsedPageList1 = parseHttpGetResponse(response1);

        final List<Map<String, Object>> parsedPagesList = new ArrayList<>(parsedPageList1);

        for (int i = 2; i <= pageCount; i++) {
            final HttpGet httpGet = sendHttpGetRequest(pageCount);
            final String response = getHttpGetResponse(httpGet);
            final List<Map<String, Object>> parsedPageList = parseHttpGetResponse(response);

            parsedPagesList.addAll(parsedPageList);
        }

        final JSONArray parsedPagesJsonArray = new JSONArray(parsedPagesList);
        saveParsedResponse(parsedPagesJsonArray);

        printSummary(pageCount, productCount);
    }

    private HttpGet sendHttpGetRequest(int page) throws URISyntaxException {
        final String maleClothingCategoryId = "20290";

        final URIBuilder uriBuilder = new URIBuilder("https://api-cloud.aboutyou.de/v1/products");
        uriBuilder.setParameter("filters[category]", maleClothingCategoryId);
        uriBuilder.setParameter("with", "attributes:key(name|brand|color),priceRange");
        uriBuilder.setParameter("page", String.valueOf(page));
        uriBuilder.setParameter("perPage", "400");

        final URI uri = uriBuilder.build();

        return new HttpGet(uri);
    }

    private String getHttpGetResponse(HttpGet httpGet) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse httpResponse = httpClient.execute(httpGet)) {
            final HttpEntity entity = httpResponse.getEntity();

            try (InputStream entityInputStream = entity.getContent()) {
                final StringBuilder jsonStringBuilder = new StringBuilder();

                int entityByte;

                while ((entityByte = entityInputStream.read()) != -1) {
                    jsonStringBuilder.append((char)entityByte);
                }

                return jsonStringBuilder.toString();
            }
        } catch (IOException e) {
            return "";
        }
    }

    private int getPageCount(String response) {
        final JSONObject responseJsonObject = new JSONObject(response);
        final JSONObject paginationJsonObject = responseJsonObject.getJSONObject("pagination");

        return paginationJsonObject.getInt("last");
    }

    private int getProductCount(String response) {
        final JSONObject responseJsonObject = new JSONObject(response);
        final JSONObject paginationJsonObject = responseJsonObject.getJSONObject("pagination");

        return paginationJsonObject.getInt("total");
    }

    private void printSummary(int triggeredHttpRequests, int productCount) {
        System.out.println("Amount of triggered HTTP requests: " + triggeredHttpRequests);
        System.out.println("Amount of extracted products: " + productCount);
    }

    private List<Map<String, Object>> parseHttpGetResponse(String response) {
        final List<Map<String, Object>> parsedList = new ArrayList<>();

        final JSONObject responseJsonObject = new JSONObject(response);
        final JSONArray entitiesJsonArray = responseJsonObject.getJSONArray("entities");

        for (Object entityObject : entitiesJsonArray) {
            final Map<String, Object> productInfoMap = new LinkedHashMap<>();

            final JSONObject entityJsonObject = (JSONObject)entityObject;

            final JSONObject attributesJsonObject = entityJsonObject.getJSONObject("attributes");

            final JSONObject nameJsonObject = attributesJsonObject.getJSONObject("name");
            final String productName = getLabel(nameJsonObject);
            productInfoMap.put("productName", productName);

            final JSONObject brandJsonObject = attributesJsonObject.getJSONObject("brand");
            final String brand = getLabel(brandJsonObject);
            productInfoMap.put("brand", brand);

            final JSONObject colorJsonObject = attributesJsonObject.getJSONObject("color");
            final JSONArray colorValuesJsonArray = colorJsonObject.getJSONArray("values");
            final List<String> colorList = new ArrayList<>();
            for (Object colorValueObject : colorValuesJsonArray) {
                final JSONObject colorValueJsonObject = (JSONObject)colorValueObject;
                final String color = colorValueJsonObject.getString("label");
                colorList.add(color);
            }

            productInfoMap.put("colors", colorList);

            final JSONObject priceRangeJsonObject = entityJsonObject.getJSONObject("priceRange");
            final JSONObject minPriceJsonObject = priceRangeJsonObject.getJSONObject("min");
            final int minPriceWithTax = minPriceJsonObject.getInt("withTax");
            final JSONObject maxPriceJsonObject = priceRangeJsonObject.getJSONObject("max");
            final int maxPriceWithTax = maxPriceJsonObject.getInt("withTax");
            final JSONObject priceJsonObject = new JSONObject();
            priceJsonObject.put("min", minPriceWithTax);
            priceJsonObject.put("max", maxPriceWithTax);
            productInfoMap.put("price", priceJsonObject);

            final int id = entityJsonObject.getInt("id");
            productInfoMap.put("articleId", id);

            parsedList.add(productInfoMap);
        }

        return parsedList;
    }

    private String getLabel(JSONObject nameJsonObject) {
        final JSONObject valuesJsonObject = nameJsonObject.getJSONObject("values");
        return valuesJsonObject.getString("label");
    }

    private void saveParsedResponse(JSONArray parsedJsonArray) throws IOException {
        final String parsedJson = parsedJsonArray.toString(4);
        final Path path = Paths.get("src/main/resources/out.json");

        Files.deleteIfExists(path);
        Files.createFile(path);
        Files.write(path, parsedJson.getBytes());
    }
}

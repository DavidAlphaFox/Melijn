package me.melijn.jda.utils;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class YTSearch {

    //https://github.com/youtube/api-samples/blob/master/java/src/main/java/com/google/api/services/samples/youtube/cmdline/data/Search.java
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();

    private static final String PROPERTIES_FILENAME = "youtube.properties";
    private static YouTube youtube;
    private static Properties properties;

    public YTSearch() {
        properties = new Properties();
        try {
            InputStream in = YouTube.Search.class.getResourceAsStream("/" + PROPERTIES_FILENAME);
            properties.load(in);
        } catch (IOException e) {
            System.err.println("There was an error reading " + PROPERTIES_FILENAME + ": " + e.getCause() + " : " + e.getMessage());
            System.exit(1);
        }
        youtube = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, request -> {
        }).setApplicationName("youtube-search").build();
    }

    public String search(String query) {
        try {
            YouTube.Search.List search = youtube.search().list("id");


            // Set your developer key from the {{ Google Cloud Console }} for
            // non-authenticated requests. See:
            // {{ https://cloud.google.com/console }}
            String apiKey = properties.getProperty("youtube.apikey");
            search.setKey(apiKey);
            search.setQ(query);

            // Restrict the search results to only include videos. See:
            // https://developers.google.com/youtube/v3/docs/search/list#type
            search.setType("video");

            // To increase efficiency, only retrieve the fields that the
            // application uses.
            search.setFields("items(id/videoId)");
            search.setMaxResults(1L);

            // Call the API adn return results
            SearchListResponse searchResponse = search.execute();
            List<SearchResult> searchResultList = searchResponse.getItems();
            if (searchResultList.size() == 0) return null;
            return searchResultList.get(0).getId().getVideoId();
        } catch (GoogleJsonResponseException e) {
            System.err.println("There was a service error: " + e.getDetails().getCode() + " : " + e.getDetails().getMessage());
        } catch (IOException e) {
            System.err.println("There was an IO error: " + e.getCause() + " : " + e.getMessage());
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;
    }

    public Map<String, String> searchMore(String query) {
        try {
            YouTube.Search.List search = youtube.search().list("id,snippet");


            // Set your developer key from the {{ Google Cloud Console }} for
            // non-authenticated requests. See:
            // {{ https://cloud.google.com/console }}
            String apiKey = properties.getProperty("youtube.apikey");
            search.setKey(apiKey);
            search.setQ(query);

            // Restrict the search results to only include videos. See:
            // https://developers.google.com/youtube/v3/docs/search/list#type
            search.setType("video");

            // To increase efficiency, only retrieve the fields that the
            // application uses.
            search.setFields("items(id/videoId, snippet/title)");
            search.setMaxResults(5L);

            // Call the API adn return results
            SearchListResponse searchResponse = search.execute();
            List<SearchResult> searchResultList = searchResponse.getItems();
            if (searchResultList.size() == 0) return null;
            Map<String, String> searched = new HashMap<>();
            for (SearchResult searchResult : searchResultList) {
                searched.put(searchResult.getId().getVideoId(), searchResult.getSnippet().getTitle());
            }
            return searched;
        } catch (GoogleJsonResponseException e) {
            System.err.println("There was a service error: " + e.getDetails().getCode() + " : " + e.getDetails().getMessage());
        } catch (IOException e) {
            System.err.println("There was an IO error: " + e.getCause() + " : " + e.getMessage());
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;
    }
}
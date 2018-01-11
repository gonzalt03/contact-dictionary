package controllers;

import com.google.gson.*;
import components.Normalizer;
import models.Contact;
import models.User;
import oauth.google.Connections;
import oauth.google.GoogleAuthAccessToken;
import oauth.google.GoogleContact;
import oauth.twitter.TwitterUsers;
import oauth.twitter.Users;
import play.Logger;
import play.Play;
import play.libs.OAuth;
import play.libs.OAuth.ServiceInfo;
import play.libs.WS;
import play.libs.WS.*;
import play.mvc.Controller;

import java.util.ArrayList;
import java.util.List;

public class GoogleAuthentication extends Controller {

    private static String URL_GET_AUTHORIZATION = "https://accounts.google.com/o/oauth2/v2/auth?" +
            "scope=https://www.googleapis.com/auth/contacts.readonly&" +
            "access_type=offline&" +
            "include_granted_scopes=true&" +
            "state=state_parameter_passthrough_value&" +
            "redirect_uri=http://localhost:9000/google/import&" +
            "response_type=code&" +
            "client_id=292609518544-em0pi9k905jiqou359gj2v2gng3hhkqo.apps.googleusercontent.com";

    private static String URL_GET_CONTACTS = "https://people.googleapis.com/v1/people/me/connections?personFields=names,emailAddresses,addresses,photos";

    private static String GET_TOKEN = "https://www.googleapis.com/oauth2/v4/token";

    public static void authenticate() {
        redirect(URL_GET_AUTHORIZATION);
    }

    public static void index(String code) {
        Logger.info("code=" + code);

        HttpResponse res = WS.url(GET_TOKEN)
                .setParameter("code", code)
                .setParameter("client_id", "292609518544-em0pi9k905jiqou359gj2v2gng3hhkqo.apps.googleusercontent.com")
                .setParameter("client_secret", "3NE89YfQXkX14BjPLQkdYAQg")
                .setParameter("redirect_uri", "http://localhost:9000/google/import")
                .setParameter("grant_type", "authorization_code")
                .post();

        try {
            Logger.info("Result request : " + res.getStatus());
            if (res.getStatus() != 200) {
                throw new Exception("Exception : " + res.getString());
            }
            Logger.info(res.getString());

            Gson gson = new Gson();
            GoogleAuthAccessToken googleAuthAccessToken = gson.fromJson(res.getString(), GoogleAuthAccessToken.class);

            List<Connections> connections = getContacts(googleAuthAccessToken);
            Normalizer normalizer = new Normalizer();
            normalizer.normalizeGoogleUsers(connections);
            renderArgs.put("title", connections.size() + " contacts");
            render();
        } catch (Exception e) {
            e.printStackTrace();
            redirect("/error?message=" + e.getMessage());
        }

    }

    private static List<Connections> getContacts(GoogleAuthAccessToken googleAuthAccessToken) throws Exception {
        List<Connections> users = new ArrayList<Connections>(0);
        String nextCursor = "";
        while (nextCursor != null) {
            GoogleContact googleContact = getNextCursor(googleAuthAccessToken, nextCursor);
            nextCursor = googleContact.getNextPageToken();
            if("".equals(nextCursor)){
                nextCursor = null;
            }
            users.addAll(googleContact.getConnections());
        }
        return users;
    }

    private static GoogleContact getNextCursor(GoogleAuthAccessToken googleAuthAccessToken, String nextCursor) throws Exception {
        Gson gson = new Gson();
        String params = "";
        if (!"".equals(nextCursor)) {
            params = "&pageToken=" + nextCursor;
        }
        HttpResponse res = WS.url(URL_GET_CONTACTS + params)
                .setHeader("Authorization", "Bearer " + googleAuthAccessToken.getAccess_token())
                .get();

        Logger.info("Result request : " + res.getStatus());
        if (res.getStatus() != 200) {
            throw new Exception("Exception : " + res.getString());
        }
        Logger.info(res.getString());

        return gson.fromJson(res.getString(), GoogleContact.class);
    }

}
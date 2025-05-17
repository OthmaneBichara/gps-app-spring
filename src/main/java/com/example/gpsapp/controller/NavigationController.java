package com.example.gpsapp.controller;

import com.example.gpsapp.model.Destination;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
public class NavigationController {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public NavigationController() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @GetMapping("/")
    public String showHomePage(Model model) {
        model.addAttribute("destination", new Destination());
        return "index";
    }

    @PostMapping("/calculate")
    public String calculateRoute(@ModelAttribute Destination destination, Model model) {
        try {
            System.out.println("Début du calcul pour: " + destination.getAddress());

            // 1. Géocodage
            String endCoords = geocodeAddress(destination.getAddress());
            if (endCoords == null) {
                destination.setEstimatedTime("Adresse introuvable");
                model.addAttribute("destination", destination);
                return "result";
            }


            String startCoords = destination.getStartCoords();

            calculateRouteInfo(startCoords, endCoords, destination);

        } catch (Exception e) {
            destination.setEstimatedTime("Erreur lors du calcul");
            e.printStackTrace();
        }

        model.addAttribute("destination", destination);
        return "result";
    }

    private String geocodeAddress(String address) throws Exception {
        String encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8);
        String url = "https://nominatim.openstreetmap.org/search?q=" + encodedAddress
                + "&format=json&limit=1";

        System.out.println("Requête Nominatim: " + url);

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "GPSApp/1.0 (contact@example.com)");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        Thread.sleep(1000); // Respect du taux de requêtes

        String response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class).getBody();
        System.out.println("Réponse Nominatim: " + response);

        JsonNode results = objectMapper.readTree(response);
        if (results.isEmpty()) {
            return null;
        }

        String lon = results.get(0).path("lon").asText();
        String lat = results.get(0).path("lat").asText();
        System.out.println("Coordonnées trouvées: " + lon + "," + lat);

        return lon + "," + lat;
    }

    private void calculateRouteInfo(String start, String end, Destination destination) throws Exception {
        String url = "https://router.project-osrm.org/route/v1/driving/"
                + start + ";" + end + "?overview=full&geometries=geojson";

        System.out.println("Requête OSRM: " + url);

        String response = restTemplate.getForObject(url, String.class);
        System.out.println("Réponse OSRM: " + response);

        JsonNode route = objectMapper.readTree(response).path("routes").get(0);

        destination.setRouteGeometry(route.path("geometry").toString());

        // Calcul de la durée
        int seconds = route.path("duration").asInt();
        int minutes = (int) Math.ceil(seconds / 60.0);
        destination.setEstimatedTime(minutes + " minutes");

        // Calcul de la distance
        int meters = route.path("distance").asInt();
        String distance = (meters > 1000) ?
                (meters/1000) + " km" : meters + " m";
        destination.setDistance(distance);
        destination.setEndCoords(end);
    }
}
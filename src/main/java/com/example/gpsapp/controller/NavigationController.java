package com.example.gpsapp.controller;

import com.example.gpsapp.model.Destination;
import com.example.gpsapp.model.Poi;
import com.example.gpsapp.repository.PoiRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class NavigationController {

    private final PoiRepository poiRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Injection via constructeur (meilleure pratique)
    public NavigationController(PoiRepository poiRepository,
                                RestTemplate restTemplate,
                                ObjectMapper objectMapper) {
        this.poiRepository = poiRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
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
                model.addAttribute("showStartButton", false);  // <-- Ajouté
                model.addAttribute("destination", destination);
                return "result";
            }

            String startCoords = destination.getStartCoords();
            calculateRouteInfo(startCoords, endCoords, destination);

            // 2. Toujours montrer le bouton si l'adresse est valide
            model.addAttribute("showStartButton", true);  // <-- Ajouté

        } catch (Exception e) {
            destination.setEstimatedTime("Erreur lors du calcul");
            model.addAttribute("showStartButton", false);  // <-- Ajouté
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

    @GetMapping("/start-navigation")
    public String startNavigation(
            @RequestParam String start,
            @RequestParam String end,
            Model model) throws Exception {

        Destination destination = new Destination();
        calculateRouteInfo(start, end, destination);
        model.addAttribute("destination", destination);
        return "navigation"; // Assurez-vous d'avoir navigation.html dans templates/
    }


    @GetMapping("/pois")
    @ResponseBody
    public List<Poi> getAllPois() {
        return poiRepository.findAll();
    }

    @PostMapping("/pois")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addPoi(@RequestBody Map<String, Object> poiData) {
        try {
            Poi poi = new Poi();
            poi.setType((String) poiData.get("type"));
            poi.setLatitude(Double.parseDouble(poiData.get("latitude").toString()));
            poi.setLongitude(Double.parseDouble(poiData.get("longitude").toString()));

            Poi savedPoi = poiRepository.save(poi);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", savedPoi);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }

    }
    @DeleteMapping("/pois/{id}")
    @ResponseBody
    public ResponseEntity<String> deletePoi(@PathVariable Long id) {
        try {
            if (!poiRepository.existsById(id)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("POI non trouvé");
            }
            poiRepository.deleteById(id);
            return ResponseEntity.ok("POI supprimé");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur: " + e.getMessage());
        }
    }


}

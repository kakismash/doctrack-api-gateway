package com.kaki.doctrack.apigateway;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/redirect-angular")
public class RedirectController {

    @GetMapping("/{fullPath:.*}")
    public ResponseEntity<Void> redirectToAngular(
            @PathVariable String fullPath,
            @RequestParam Map<String, String> queryParams) throws URISyntaxException {

        // Convert query parameters into a string
        String queryString = queryParams.isEmpty() ? "" :
                "?" + queryParams.entrySet()
                        .stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining("&"));

        // Construct the final redirect URL
        String redirectUrl = "http://localhost:4200/app/" + fullPath + queryString;

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(new URI(redirectUrl));

        return new ResponseEntity<>(headers, HttpStatus.FOUND); // 302 Redirect
    }
}
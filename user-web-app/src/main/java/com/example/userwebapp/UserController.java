package com.example.userwebapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Controller
public class UserController {

    private final String userServiceBaseUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    public UserController(@Value("${services.user.base-url}") String userServiceBaseUrl) {
        this.userServiceBaseUrl = userServiceBaseUrl;
    }

    @GetMapping(path = "/log-in")
    public String getLogIn(ModelMap model) {
        model.addAttribute("user", new User());
        return "log_in.jsp";
    }

    @PostMapping(path = "/log-in")
    public String authenticate(ModelMap model, @ModelAttribute User user) {
        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(
                    userServiceBaseUrl + "/users/authenticate",
                    user,
                    Void.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                model.addAttribute("name", user.getName());
                return "dashboard.jsp";
            }
        } catch (HttpClientErrorException.Unauthorized error) {
            model.addAttribute("test", "Incorrect name or password. Please try again.");
            return "log_in.jsp";
        } catch (RestClientException error) {
            model.addAttribute("test", "User service is unavailable. Please try again in a moment.");
            return "log_in.jsp";
        }

        model.addAttribute("test", "Could not sign in. Please try again.");
        return "log_in.jsp";
    }

    @GetMapping(path = "/create-user")
    public String getCreateUser(ModelMap model) {
        model.addAttribute("user", new User());
        return "create_user.jsp";
    }

    @PostMapping(path = "/create-user")
    public String createUser(ModelMap model, @ModelAttribute User user) {
        try {
            User createdUser = restTemplate.postForObject(
                    userServiceBaseUrl + "/users",
                    user,
                    User.class);
            model.addAttribute("user", createdUser);
            model.addAttribute("name", user.getName());
            return "dashboard.jsp";
        } catch (RestClientException error) {
            model.addAttribute("test", "Could not create the user. Check the user service and try again.");
            return "create_user.jsp";
        }
    }

    @GetMapping(path = "/delete-user")
    public String getDeleteUser(ModelMap model) {
        model.addAttribute("user", new User());
        return "delete_user.jsp";
    }

    @PostMapping(path = "/delete-user")
    public String deleteUser(ModelMap model, @ModelAttribute User user) {
        try {
            restTemplate.delete(userServiceBaseUrl + "/users?name={name}", user.getName());
            return "log_in.jsp";
        } catch (RestClientException error) {
            model.addAttribute("test", "Could not delete the user. Check the user service and try again.");
            return "delete_user.jsp";
        }
    }

    @GetMapping(path = "/edit-user")
    public String getEditUser(ModelMap model) {
        model.addAttribute("user", new User());
        return "edit_user.jsp";
    }

    @PostMapping(path = "/edit-user")
    public String editUser(ModelMap model, @ModelAttribute User user) {
        try {
            restTemplate.put(userServiceBaseUrl + "/users/{id}", user, user.getId());
            model.addAttribute("user", user);
            return "log_in.jsp";
        } catch (RestClientException error) {
            model.addAttribute("test", "Could not update the user. Check the user service and try again.");
            return "edit_user.jsp";
        }
    }
}

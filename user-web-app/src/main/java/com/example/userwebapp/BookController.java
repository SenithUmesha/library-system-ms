package com.example.userwebapp;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class BookController {

    private final String bookServiceBaseUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    public BookController(@Value("${services.book.base-url}") String bookServiceBaseUrl) {
        this.bookServiceBaseUrl = bookServiceBaseUrl;
    }

    @GetMapping(path = "/all-books")
    public String getAllBooks(ModelMap model) {
        model.addAttribute("book", new Book());
        return "all_books.jsp";
    }

    @PostMapping(path = "/all-books")
    public String allBooks(ModelMap model) {
        try {
            List<Book> bookList = restTemplate.getForObject(
                    bookServiceBaseUrl + "/books",
                    List.class);
            model.addAttribute("searchedBook", bookList);
        } catch (RestClientException error) {
            model.addAttribute("test", "Book service is unavailable. Please try again in a moment.");
        }
        return "all_books.jsp";
    }

    @GetMapping(path = "/search-books-name")
    public String getBooksByName(ModelMap model) {
        model.addAttribute("book", new Book());
        return "search_books_name.jsp";
    }

    @PostMapping(path = "/search-books-name")
    public String getBooksByName(ModelMap model, @ModelAttribute Book book) {
        String url = UriComponentsBuilder.fromHttpUrl(bookServiceBaseUrl + "/books/search")
                .queryParam("name", book.getBook_name())
                .toUriString();
        searchBook(model, url);
        return "search_books_name.jsp";
    }

    @GetMapping(path = "/search-books-author")
    public String getBooksByAuthor(ModelMap model) {
        model.addAttribute("book", new Book());
        return "search_books_author.jsp";
    }

    @PostMapping(path = "/search-books-author")
    public String getBooksByAuthor(ModelMap model, @ModelAttribute Book book) {
        String url = UriComponentsBuilder.fromHttpUrl(bookServiceBaseUrl + "/books/search")
                .queryParam("author", book.getBook_author())
                .toUriString();
        searchBook(model, url);
        return "search_books_author.jsp";
    }

    private void searchBook(ModelMap model, String url) {
        try {
            Book searchedBook = restTemplate.getForObject(url, Book.class);
            model.addAttribute("searchedBook", searchedBook);
        } catch (HttpClientErrorException.NotFound error) {
            model.addAttribute("test", "No matching book was found.");
        } catch (RestClientException error) {
            model.addAttribute("test", "Book service is unavailable. Please try again in a moment.");
        }
    }
}

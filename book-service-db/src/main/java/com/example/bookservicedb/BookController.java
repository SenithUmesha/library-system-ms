package com.example.bookservicedb;

import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/books")
public class BookController {

    private final BookRepository bookRepository;

    public BookController(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @GetMapping
    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    @GetMapping("/search")
    public ResponseEntity<Book> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String author) {
        Optional<Book> book;

        if (name != null && !name.trim().isEmpty()) {
            book = bookRepository.getBookName(name.trim());
        } else if (author != null && !author.trim().isEmpty()) {
            book = bookRepository.getBookAuthor(author.trim());
        } else {
            return ResponseEntity.badRequest().build();
        }

        return book.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

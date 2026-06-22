package com.linktracker.scrapper.api.rest.controllers;

import com.linktracker.scrapper.application.chat.ScrapperChatUseCase;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for chat registration lifecycle.
 */
@RestController
@RequiredArgsConstructor
public class TgChatController {

    private final ScrapperChatUseCase scrapperChatUseCase;

    /**
     * Registers a telegram chat for tracking.
     *
     * @param chatId positive telegram chat identifier
     * @return HTTP 200 on success
     */
    @PostMapping("/tg-chat/{id}")
    public ResponseEntity<Void> registerChat(@PathVariable("id") @Positive long chatId) {
        scrapperChatUseCase.registerChat(chatId);
        return ResponseEntity.ok().build();
    }

    /**
     * Deletes a telegram chat from tracking.
     *
     * @param chatId positive telegram chat identifier
     * @return HTTP 200 on success
     */
    @DeleteMapping("/tg-chat/{id}")
    public ResponseEntity<Void> deleteChat(@PathVariable("id") @Positive long chatId) {
        scrapperChatUseCase.deleteChat(chatId);
        return ResponseEntity.ok().build();
    }
}

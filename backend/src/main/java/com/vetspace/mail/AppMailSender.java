package com.vetspace.mail;

/** Outbound transactional mail port. Named distinctly from Spring's own MailSender to avoid import ambiguity. */
public interface AppMailSender {

    void send(String to, String subject, String body);

    /** Variant with a Reply-To (e.g. support messages answered directly to the student). */
    default void send(String to, String subject, String body, String replyTo) {
        send(to, subject, body);
    }
}

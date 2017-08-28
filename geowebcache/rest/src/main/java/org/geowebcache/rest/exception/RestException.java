package org.geowebcache.rest.exception;

import org.springframework.http.HttpStatus;

public class RestException extends RuntimeException {
    /** serialVersionUID */
    private static final long serialVersionUID = 5762645820684796082L;

    private final HttpStatus status;

    public RestException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public RestException(String message, HttpStatus status, Throwable t) {
        super(message, t);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(getClass().getName());
        if( status != null ){
            builder.append(" ");
            builder.append(status.value());
            builder.append(" ");
            builder.append(status.name());
        }
        String message = getLocalizedMessage();
        if( message != null ){
            builder.append(": ");
            builder.append( message );
        }
        return builder.toString();
    }
}
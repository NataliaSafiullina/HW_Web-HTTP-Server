package ru.safiullina;

import org.apache.commons.fileupload2.core.RequestContext;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

public class RequestContextImpl implements RequestContext {
    private long contentLength;
    private String contentType;
    private InputStream inputStream;

    public RequestContextImpl(long contentLength, String contentType, InputStream inputStream) {
        this.contentLength = contentLength;
        this.contentType = contentType;
        this.inputStream = inputStream;
    }

    @Override
    public String getCharacterEncoding() {
        return null;
    }

    @Override
    public Charset getCharset() throws UnsupportedCharsetException {
        return Charset.defaultCharset();
    }

    @Override
    public long getContentLength() {
        return contentLength;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }
}

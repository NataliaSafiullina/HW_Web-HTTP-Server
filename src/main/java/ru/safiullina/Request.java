package ru.safiullina;

import org.apache.http.NameValuePair;

import java.util.ArrayList;
import java.util.List;

public class Request {

    protected final String method;
    protected final String path;
    protected final List<NameValuePair> params;

    protected final List<String> headers;

    public Request(String method, String path, List<NameValuePair> params, List<String> headers) {
        this.method = method;
        this.path = path;
        this.params = params;
        this.headers = headers;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public List<NameValuePair> getQueryParam(String name) {
        List<NameValuePair> resultList = new ArrayList<>();
        for (NameValuePair pair : params) {
            if (pair.getName().equals(name)) {
                resultList.add(pair);
            }
        }
        return resultList;
    }

    public List<NameValuePair> getQueryParams() {
        return params;
    }

    public List<String> getHeaders() {
        return headers;
    }
}

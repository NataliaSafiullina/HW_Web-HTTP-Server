package ru.safiullina;

import org.apache.commons.fileupload2.core.DiskFileItem;
import org.apache.commons.fileupload2.core.FileItem;
import org.apache.http.NameValuePair;

import java.util.ArrayList;
import java.util.List;

public class Request {

    protected final String method;
    protected final String path;
    protected final List<NameValuePair> params;
    protected final List<String> headers;
    protected final List<NameValuePair> bodyParams;
    protected final List<DiskFileItem> fileItems;

    public Request(String method, String path, List<NameValuePair> params, List<String> headers, List<NameValuePair> bodyParams, List<DiskFileItem> fileItems) {
        this.method = method;
        this.path = path;
        this.params = params;
        this.headers = headers;
        this.bodyParams = bodyParams;
        this.fileItems = fileItems;
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

    public List<NameValuePair> getPostParam(String name) {
        List<NameValuePair> resultList = new ArrayList<>();
        for (NameValuePair pair : bodyParams) {
            if (pair.getName().equals(name)) {
                resultList.add(pair);
            }
        }
        return resultList;
    }

    public List<NameValuePair> getPostParams() {
        return bodyParams;
    }

    public List<DiskFileItem> getPart(String name) {
        List<DiskFileItem> resultList = new ArrayList<>();
        for (DiskFileItem item : fileItems) {
            if (!item.isFormField() & item.getFieldName().equals(name)) {
                resultList.add(item);
            }
        }
        return resultList;
    }

    public List<DiskFileItem> getParts() {
        return fileItems;
    }
}

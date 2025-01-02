package ru.safiullina;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class RequestTest {
    Request request;

    @BeforeEach
    void createRequest() {
        String method = "GET";
        String path = "/path";
        List<NameValuePair> params = URLEncodedUtils.parse("first=one&second=two&second=two", StandardCharsets.UTF_8);
        List<String> headers = Arrays.asList("Host: localhost:9999\r\n" +
                "Connection: keep-alive\r\n" +
                "sec-ch-ua: \"Chromium\";v=\"122\", \"Not(A:Brand\";v=\"24\", \"Google Chrome\";v=\"122\"\r\n" +
                "sec-ch-ua-mobile: ?0\r\n" +
                "sec-ch-ua-platform: \"Windows\"\r\n" +
                "Upgrade-Insecure-Requests: 1\r\n" +
                "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36\r\n" +
                "Sec-Purpose: prefetch;prerender\r\n" +
                "Purpose: prefetch\r\n" +
                "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7\r\n" +
                "Sec-Fetch-Site: none\r\n" +
                "Sec-Fetch-Mode: navigate\r\n" +
                "Sec-Fetch-User: ?1\r\n" +
                "Sec-Fetch-Dest: document\r\n" +
                "Accept-Encoding: gzip, deflate, br, zstd\r\n" +
                "Accept-Language: en,ru;q=0.9,ru-RU;q=0.8,en-US;q=0.7".split("\r\n"));
        List<NameValuePair> bodyParams = URLEncodedUtils.parse("login=qwe&password=qwerty&login=qwe", StandardCharsets.UTF_8);

        // Создаем объект Request
        request = new Request(method, path, params, headers, bodyParams);
    }

    @Test
    void getQueryParam() {
        for (NameValuePair pair : request.getQueryParam("second")) {
            System.out.printf("Ключ = %s, значение = %s \n", pair.getName(), pair.getValue());
            assertThat("two", is(pair.getValue()));
        }
    }

    @Test
    void getPostParam() {
        for (NameValuePair pair : request.getPostParam("login")) {
            System.out.printf("Ключ = %s, значение = %s \n", pair.getName(), pair.getValue());
            assertThat("qwe", is(pair.getValue()));
        }
    }
}
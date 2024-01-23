package ru.dimangan;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.concurrent.TimedSemaphore;
import org.apache.http.HttpHeaders;


import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;


public class CrptApi{
    private static final String CREATE_DOCUMENT_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final TimedSemaphore timedSemaphore;
    private final CloseableHttpClient httpClient;
    public static void main(String[] args) {
        //Пример использования
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 5);
        int threadNum = 10;
        try(ExecutorService executorService = Executors.newFixedThreadPool(threadNum)) {
            for (int i = 0; i < threadNum; i++) {
                executorService.execute(() -> {
                    HttpResponse httpResponse= crptApi.createDocumentRequest(new Document(), "sign");
                    try {
                        System.out.println(Thread.currentThread().getName() + "\n" +
                                EntityUtils.toString(httpResponse.getEntity(), "UTF-8") + "\n");
                    }
                    catch(IOException e){
                        e.printStackTrace();
                    }
                });
            }
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(threadNum / crptApi.getLimit(), crptApi.getTimeUnit())) {
                    executorService.shutdownNow();
                }
            }
            finally {
                executorService.shutdownNow();
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
        crptApi.shutdown();
    }
    public CrptApi() {
        this(TimeUnit.MINUTES, 5);
    }
    public CrptApi(TimeUnit requestLimitTimeUnit, int requestLimit){
        this.timedSemaphore = new TimedSemaphore(1, requestLimitTimeUnit, requestLimit);
        this.httpClient = HttpClientBuilder.create().build();
    }
    public HttpResponse createDocumentRequest(Document document, String signature){
        try {
            this.timedSemaphore.acquire();
            String jsonDocument = new ObjectMapper().writeValueAsString(document);
            HttpPost createRequest = new HttpPost(CREATE_DOCUMENT_URL);
            createRequest.addHeader(HttpHeaders.AUTHORIZATION, signature);
            createRequest.addHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            String requestBody = String.format("{ \"product_document\": \"%s\", \"document_format\": \"MANUAL\", \"type\": \"LP_INTRODUCE_GOODS\", \"signature\": \"%s\" }",
                    jsonDocument, signature);
            StringEntity entity = new StringEntity(requestBody);
            createRequest.setEntity(entity);
            HttpResponse createResponse = httpClient.execute(createRequest);
            if(createResponse.getStatusLine().getStatusCode() == 200){
                System.out.println("Document created");
            }
            else{
                System.out.println("Creation failed. Status code " + createResponse.getStatusLine().getStatusCode());
            }
            return createResponse;
        }
        catch (IOException | InterruptedException e){
            e.printStackTrace();
        }
        return null;
    }
    public int getLimit(){
        return this.timedSemaphore.getLimit();
    }
    public TimeUnit getTimeUnit(){
        return this.timedSemaphore.getUnit();
    }
    public void shutdown(){
        this.timedSemaphore.shutdown();
    }
    @Data
    public static class Description{
        private String participantInn;
    }
    @Data
    public static class Products{
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;
    }
    @Data
    @AllArgsConstructor
    public static class Document{
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Products> products;
        private String reg_date;
        private String reg_number;
        public Document(){
            description = new Description();
            products = new ArrayList<>();
        }
    }
}
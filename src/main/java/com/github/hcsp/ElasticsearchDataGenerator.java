package com.github.hcsp;

import org.apache.http.HttpHost;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ElasticsearchDataGenerator {

    public static void main(String[] args) throws IOException {
        SqlSessionFactory sqlSessionFactory;
        try {
            String resource = "db/mybatis/config.xml";
            InputStream inputStream = Resources.getResourceAsStream(resource);
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<News> newsFromMySQL = getNewsFromMysql(sqlSessionFactory);
        try( RestHighLevelClient client = new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost("localhost", 9200, "http")))) {
            for (News news : newsFromMySQL) {
                IndexRequest request = new IndexRequest("news");
                Map<String, Object> data = new HashMap<>();
                data.put("content", news.getContent());
                data.put("url", news.getUrl());
                data.put("title", news.getTitle());
                data.put("createdAt", news.getCreatedAt());
                data.put("modifiedAt", news.getModifiedAt());

                request.source(data, XContentType.JSON);
                IndexResponse response = client.index(request, RequestOptions.DEFAULT);
                System.out.println(
                    response.status().getStatus()
                );
            }
        }



    }

    private static List<News> getNewsFromMysql(SqlSessionFactory sqlSessionFactory) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            return session.selectList("com.github.hcsp.MockMapper.selectNews");
        }
    }
}

package com.github.hcsp.io;


import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static final String USER_NAME = "root";
    public static final String PASSWORD = "root";

    private static List<String> loadUrlsFromDatabase(Connection connection, String sql) throws SQLException {
        List<String> results = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                results.add(resultSet.getString(1));
            }
        }
        return results;
    }


    @SuppressFBWarnings("DMI_CONSTANT_DB_PASSWORD")
    public static void main(String[] args) throws IOException, SQLException {


        // 待处理的链接池
        // 从数据库加载即将处理的链接的代码
        Connection connection = DriverManager.getConnection("jdbc:h2:file:/Users/ories/Downloads/java-zhangbo/30项目实战 - 多线程网络爬虫与Elasticsearch新闻搜索引擎/project/xiedaimala-crawler/news", USER_NAME, PASSWORD);

        while (true) {

            List<String> linkPool = loadUrlsFromDatabase(connection, "select link from LINKS_TO_BE_PROCESSED");

            // 已经处理的链接池子
            // 从数据库加载已经处理的链接的代码


            // 如果池子是空的，跳出循环
            if (linkPool.isEmpty()) {
                break;
            }

            // 从待处理池子中捞一个来处理，
            // 处理完后从池子(包括数据库)中删除
            String link = linkPool.remove(linkPool.size() - 1);

            insertLinkIntoDatabase(connection, link, "DELETE FROM LINKS_TO_BE_PROCESSED where link = ?");

            // 询问数据库，当前链接是不是已经处理过来
            // 判断链接是否处理过了
            if (!isLinkProcessed(connection, link)) {
                continue;
            }

            // 判断是否是需要处理的链接
            if (isInterestingLink(link)) {
                Document doc = httpGetAndParseHtml(link);

                parseUrlsFromPageAndStoreIntoDatabase(connection, doc);

                // 如果是新闻的详情页面的就储存它,否则什么都不做
                storeIntoDatabaseIfItIsNewPage(doc);

                insertLinkIntoDatabase(connection, link, "INSERT INTO LINKS_ALREADY_PROCESSED (LINK) values (?)");

                // 将处理过的链接，加入处理过的链接池
            }
        }


    }

    private static void parseUrlsFromPageAndStoreIntoDatabase(Connection connection, Document doc) throws SQLException {
        for (Element aTag : doc.select("a")) {
            String href = aTag.attr("href");
            insertLinkIntoDatabase(connection, href, "INSERT INTO LINKS_TO_BE_PROCESSED (LINK) values (?)");
        }
    }

    private static boolean isLinkProcessed(Connection connection, String link) throws SQLException {
        ResultSet resultSet = null;
        try (PreparedStatement statement = connection.prepareStatement("SELECT LINK from LINKS_ALREADY_PROCESSED where link = ?")) {
            statement.setString(1, link);
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                return true;
            }
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
        }
        return false;
    }

    private static void insertLinkIntoDatabase(Connection connection, String link, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, link);
            statement.executeUpdate();
        }
    }

    private static void storeIntoDatabaseIfItIsNewPage(Document doc) {
        ArrayList<Element> articleTags = doc.select("article");
        if (!articleTags.isEmpty()) {
            for (Element article : articleTags) {
                String title = articleTags.get(0).child(0).text();
                System.out.println(title);
            }
        }
    }

    // 这是我们感兴趣的，我们只处理新浪站内的链接
    private static Document httpGetAndParseHtml(String link) throws IOException {

        CloseableHttpClient httpclient = HttpClients.createDefault();

        if (link.startsWith("//")) {
            link = "https:" + link;
        }

        HttpGet httpGet = new HttpGet(link);
        httpGet.addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1");

        try (CloseableHttpResponse response1 = httpclient.execute(httpGet)) {
            // 获取访问的响应头
            System.out.println(response1.getStatusLine());
            HttpEntity entity1 = response1.getEntity();
            String html = EntityUtils.toString(entity1);
            return Jsoup.parse(html);
        }
    }

    // 我们只关心news.sina的，我们要排除登录页面
    private static boolean isInterestingLink(String link) {
        return (isNewsPage(link) || isIndexPage(link)) && isNotLoginPage(link);
    }

    private static boolean isIndexPage(String link) {
        return "https://sina.cn".equals(link);
    }

    private static boolean isNewsPage(String link) {
        return link.contains("news.sina.cn");
    }

    private static boolean isNotLoginPage(String link) {
        return !link.contains("passport.sina.cn");
    }
}

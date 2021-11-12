package com.github.hcsp;


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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class Crawler {
    private CrawlerDao dao = new MyBatisCrawlerDao();

    public void run() throws SQLException, IOException {
        // 待处理的链接池
        // 从数据库加载即将处理的链接的代码

        String link;
        // 从数据库中加载下一个链接，如果能加载到，则进行循环
        while ((link = dao.getNextLinkThenDelete()) != null) {
            // 询问数据库，当前链接是不是已经处理过来
            if (dao.isLinkProcessed(link)) {
                continue;
            }
            // 判断是否是需要处理的链接
            if (isInterestingLink(link)) {
                System.out.println(link);
                Document doc = httpGetAndParseHtml(link);
                parseUrlsFromPageAndStoreIntoDatabase(doc);
                // 如果是新闻的详情页面的就储存它,否则什么都不做
                storeIntoDatabaseIfItIsNewPage(doc, link);

                dao.insertProcessedLink(link);
            }
        }
    }


    public static void main(String[] args) throws IOException, SQLException {
        new Crawler().run();
    }

    private void parseUrlsFromPageAndStoreIntoDatabase(Document doc) throws SQLException {
        for (Element aTag : doc.select("a")) {
            String href = aTag.attr("href");

            if (href.startsWith("//")) {
                href = "https:" + href;
            }

            if (!href.toLowerCase().startsWith("javascript")) {
                dao.insertLinkToBeProcessed(href);
            }

        }
    }


    private void storeIntoDatabaseIfItIsNewPage(Document doc, String link) throws SQLException {
        ArrayList<Element> articleTags = doc.select("article");
        if (!articleTags.isEmpty()) {
            for (Element articleTag : articleTags) {
                String title = articleTags.get(0).child(0).text();
                String content = articleTag.select("p").stream().map(Element::text).collect(Collectors.joining("\n"));
                dao.insertNewsIntoDatabase(link, title, content);
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

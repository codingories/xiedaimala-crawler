package com.github.hcsp;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Random;

@SuppressFBWarnings("DMI_RANDOM_USED_ONLY_ONCE")
public class MockDataGenerator {
    private static void mockData(SqlSessionFactory sqlSessionFactory, int howMany) {
        try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
            List<News> currentNews = session.selectList("com.github.hcsp.MockMapper.selectNews");
            System.out.println("");
            int count = howMany - currentNews.size();
            Random random = new Random();
            try {
                while (count-- > 0) {
                    int index = random.nextInt(currentNews.size());
                    News newsToBeInserted = new News(currentNews.get(index));
                    // 拿到原先的news时间戳
                    Instant currentTime = newsToBeInserted.getCreatedAt();
                    // 减 去一个随机的秒数，0到一年
                    currentTime = currentTime.minusSeconds(random.nextInt(3600 * 24 * 365));

                    newsToBeInserted.setModifiedAt(currentTime);
                    newsToBeInserted.setCreatedAt(currentTime);

                    session.insert("com.github.hcsp.MockMapper.insertNews", newsToBeInserted);
                    System.out.println("left: " + count);
                    // 没两千条就刷进去一次，防止卡死
                    if (count % 2000 == 0) {
                        session.flushStatements();
                    }
                }
                session.commit();
            } catch (Exception e) {
                // 事务回滚，生产中银行转账之类非常必要
                session.rollback();
                throw new RuntimeException(e);

            }
        }
    }


    public static void main(String[] args) {
        SqlSessionFactory sqlSessionFactory;
        try {
            String resource = "db/mybatis/config.xml";
            InputStream inputStream = Resources.getResourceAsStream(resource);
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        mockData(sqlSessionFactory, 100_0000);

    }
}

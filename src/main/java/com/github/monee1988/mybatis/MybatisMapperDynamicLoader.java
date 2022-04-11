package com.github.monee1988.mybatis;

import com.github.monee1988.mybatis.xmlop.MybatisXmlScanner;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * 项目开发mybatis XML修改自动加载类
 * (项目不用重启，方便开发)
 *
 * @author monee1988
 */
public class MybatisMapperDynamicLoader implements DisposableBean, InitializingBean, ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(MybatisMapperDynamicLoader.class);

    /**
     * 是否开启动态加载
     */
    private Boolean isMapperDynamicLoader = false;

    /**
     * mybatis xml 扫描仪
     */
    private MybatisXmlScanner scanner = null;

    /**
     * 需要扫描的xml位置
     */
    private String[] mapperLocations;

    /**
     * 数据源
     */
    private SqlSessionFactory factory;

    public void setMapperLocations(String[] mapperLocations) {
        this.mapperLocations = mapperLocations;
    }

    public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
        this.factory = sqlSessionFactory;
    }

    public void setMapperDynamicLoader(Boolean mapperDynamicLoader) {
        isMapperDynamicLoader = mapperDynamicLoader;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    }


    @Override
    public void afterPropertiesSet() throws Exception {

        // 如果没有开启动态加载 直接跳出
        if (!isMapperDynamicLoader) {
            return;
        }
        try {
            // 触发文件监听事件
            scanner = new MybatisXmlScanner(factory, mapperLocations);
            scanner.scanAndMointerXmlChange();

            logger.debug("MybatisMapperDynamicLoader 开始监控。。。");

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void destroy() throws Exception {
        if (scanner != null) {
            scanner.shutDownTask();
        }
    }
}
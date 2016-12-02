package com.hp.core.mybatis;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.NestedIOException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;


/**
 *	项目开发mybatis XML修改自动加载类
 *	(项目不用重启，方便开发)
 */
public class MybatisMapperDynamicLoader implements DisposableBean, InitializingBean, ApplicationContextAware {
	
	private static final Logger logger = LoggerFactory.getLogger(MybatisMapperDynamicLoader.class);  
	
	private ConfigurableApplicationContext context = null;
	private HashMap<String, String> fileMapping = new HashMap<String, String>();
	private Scanner scanner = null;
	private ScheduledExecutorService service = null;
    private final List<String> changeMapers = new ArrayList<String>();
    private SqlSessionFactory factory;
    private String[] mapperLocations;

	public final void setMapperLocations(String[] mapperLocations) {
		this.mapperLocations = mapperLocations;
	}

	public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
		if(sqlSessionFactory != null){
			this.factory = sqlSessionFactory;
		}else{
			factory = context.getBean(SqlSessionFactory.class);
		}
	}

	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.context = (ConfigurableApplicationContext) applicationContext;
	}

	public void afterPropertiesSet() throws Exception {
		
		if(!logger.isDebugEnabled()){
			return;
		}
		try {
			service = Executors.newScheduledThreadPool(1);
			// 触发文件监听事件
			scanner = new Scanner();
			scanner.scan();
			service.scheduleAtFixedRate(new Task(), 5, 5, TimeUnit.SECONDS);

		} catch (Exception e1) {
			e1.printStackTrace();
		}

	}

	class Task implements Runnable {
		public void run() {
			try {
				if (scanner.isChanged()) {
					logger.info(changeMapers.toString()+"文件改变,重新加载.");
					scanner.reloadXML();
					logger.info("***Mapper.xml加载完毕");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	@SuppressWarnings({ "rawtypes" })
	class Scanner {
		
		private ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
		
		public Scanner() {}
		
		public Resource[] getResource(String mapperLocation) throws IOException {

			return resourcePatternResolver.getResources(mapperLocation);
		}

		public void reloadXML() throws Exception {
			Configuration configuration = factory.getConfiguration();
			// 移除加载项
			removeConfig(configuration);
			// 重新扫描加载
			for (String mapperLocation : mapperLocations) {
			
				Resource[] resources = getResource(mapperLocation);
				if (resources != null) {
					for (int i = 0; i < resources.length; i++) {
						if (resources[i] == null) {
							continue;
						}
						try {
							XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(resources[i].getInputStream(),
									configuration, resources[i].toString(), configuration.getSqlFragments());
							xmlMapperBuilder.parse();
						} catch (Exception e) {
							throw new NestedIOException("Failed to parse mapping resource: '" + resources[i] + "'", e);
						} finally {
							ErrorContext.instance().reset();
						}
					}
				}
//			}
			}

		}

		private void removeConfig(Configuration configuration) throws Exception {
			Class<?> classConfig = configuration.getClass();
			clearMap(classConfig, configuration, "mappedStatements");
			clearMap(classConfig, configuration, "caches");
			clearMap(classConfig, configuration, "resultMaps");
			clearMap(classConfig, configuration, "parameterMaps");
			clearMap(classConfig, configuration, "keyGenerators");
			clearMap(classConfig, configuration, "sqlFragments");

			clearSet(classConfig, configuration, "loadedResources");

		}

		private void clearMap(Class<?> classConfig, Configuration configuration, String fieldName) throws Exception {
			Field field = classConfig.getDeclaredField(fieldName);
			field.setAccessible(true);
			Map mapConfig = (Map) field.get(configuration);
			mapConfig.clear();
		}

		private void clearSet(Class<?> classConfig, Configuration configuration, String fieldName) throws Exception {
			Field field = classConfig.getDeclaredField(fieldName);
			field.setAccessible(true);
			Set setConfig = (Set) field.get(configuration);
			setConfig.clear();
		}

		public void scan() throws IOException {
			if (!fileMapping.isEmpty()) {
				return;
			}
			for (String  mapperLocation: mapperLocations) {
				Resource[] resources = getResource(mapperLocation);
				if (resources != null) {
					for (int i = 0; i < resources.length; i++) {
						String multi_key = getValue(resources[i]);
						fileMapping.put(resources[i].getFilename(), multi_key);
					}
				}
			}
		}

		private String getValue(Resource resource) throws IOException {
			String contentLength = String.valueOf((resource.contentLength()));
			String lastModified = String.valueOf((resource.lastModified()));
			return new StringBuilder(contentLength).append(lastModified).toString();
		}

		public boolean isChanged() throws IOException {
			boolean isChanged = false;
			changeMapers.clear();
			for (String  mapperLocation: mapperLocations) {
				Resource[] resources = getResource(mapperLocation);
				if (resources != null) {
					for (int i = 0; i < resources.length; i++) {
						String name = resources[i].getFilename();
						String value = fileMapping.get(name);
						String multi_key = getValue(resources[i]);
						if (!multi_key.equals(value)) {
							changeMapers.add(name);
							isChanged = true;
							fileMapping.put(name, multi_key);
						}
					}
				}
			}
			return isChanged;
		}
	}

	public void destroy() throws Exception {
		if (service != null) {
			service.shutdownNow();
		}
	}
}
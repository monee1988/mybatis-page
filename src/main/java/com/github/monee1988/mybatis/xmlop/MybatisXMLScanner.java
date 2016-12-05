package com.github.monee1988.mybatis.xmlop;

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
import org.springframework.core.NestedIOException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

public class MybatisXMLScanner {

	private ScheduledExecutorService service = null;
	
	private SqlSessionFactory sqlSession;
	
	private String [] mapperLocations;
	
	private ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
	
	private final HashMap<String, String> fileMapping = new HashMap<String, String>();
	
	private final List<String> changeMapers = new ArrayList<String>();

	public MybatisXMLScanner() {
	}

	public MybatisXMLScanner(SqlSessionFactory factory, String[] mapperLocations) {
		this.sqlSession =factory;
		this.mapperLocations =mapperLocations;
	}

	public Resource[] getResource(String mapperLocation) throws IOException {

		return resourcePatternResolver.getResources(mapperLocation);
	}

	public void reloadXML() throws Exception {
		Configuration configuration = sqlSession.getConfiguration();
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
			// }
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
		@SuppressWarnings("rawtypes")
		Map mapConfig = (Map) field.get(configuration);
		mapConfig.clear();
	}

	private void clearSet(Class<?> classConfig, Configuration configuration, String fieldName) throws Exception {
		Field field = classConfig.getDeclaredField(fieldName);
		field.setAccessible(true);
		@SuppressWarnings("rawtypes")
		Set setConfig = (Set) field.get(configuration);
		setConfig.clear();
	}

	public void scan() throws IOException {
		if (!fileMapping.isEmpty()) {
			return;
		}
		for (String mapperLocation : mapperLocations) {
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
		for (String mapperLocation : mapperLocations) {
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

	private void mointerXmlChange() {
		service = Executors.newScheduledThreadPool(1);
		service.scheduleAtFixedRate(new MointerMybatisXMLChangeTask(this,changeMapers), 5, 5, TimeUnit.SECONDS);
	}
	
	public void shutDownTask() {
		if(service != null ){
			service.shutdownNow();
		}
	}

	public void scanAndMointerXmlChange() throws Exception {
		this.scan();
		this.mointerXmlChange();
	}
	
	
	
}
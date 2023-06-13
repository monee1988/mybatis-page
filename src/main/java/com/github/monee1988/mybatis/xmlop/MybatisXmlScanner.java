package com.github.monee1988.mybatis.xmlop;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * @author monee1988
 */
public class MybatisXmlScanner {

	private ScheduledExecutorService service = null;

	private SqlSessionFactory sqlSession;

	private String[] mapperLocations;

	private final ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

	private final HashMap<String, String> fileMapping = new HashMap<>();

	private final List<String> changeMappers = new ArrayList<>();

	private final List<File> changeXmlFiles = new ArrayList<>();

	public MybatisXmlScanner() {
	}

	public MybatisXmlScanner(SqlSessionFactory factory, String[] mapperLocations) {
		this.sqlSession = factory;
		this.mapperLocations = mapperLocations;
	}

	public Resource[] getResource(String mapperLocation) throws IOException {

		return resourcePatternResolver.getResources(mapperLocation);
	}

	/**
	 *  自动加载被修改的 XML
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void reloadXml() throws Exception {
		Configuration configuration = sqlSession.getConfiguration();

		// 清理原有资源，更新为自己的StrictMap方便，增量重新加载
		String[] mapFieldNames = new String[] { "mappedStatements", "caches", "resultMaps", "parameterMaps",
				"keyGenerators", "sqlFragments" };
		for (String fieldName : mapFieldNames) {
			Field field = configuration.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			Map map = ((Map) field.get(configuration));
			if (!(map instanceof StrictMap)) {
				Map newMap = new StrictMap(StringUtils.capitalize(fieldName) + "collection");
				for (Object key : map.keySet()) {
					try {
						newMap.put(key, map.get(key));
					} catch (IllegalArgumentException ex) {
						newMap.put(key, ex.getMessage());
					}
				}
				field.set(configuration, newMap);
			}
		}

		for (File file : changeXmlFiles) {
			InputStream inputStream = new FileInputStream(file);
			String resource = file.getAbsolutePath();
			
			// 清理已加载的资源标识，方便让它重新加载。
			Field loadedResourcesField = configuration.getClass().getDeclaredField("loadedResources");
			loadedResourcesField.setAccessible(true);
			Set loadedResourcesSet = ((Set) loadedResourcesField.get(configuration));
			loadedResourcesSet.remove(resource);
			try {
				XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(inputStream, configuration, resource,
						configuration.getSqlFragments());
				xmlMapperBuilder.parse();
			} catch (Exception e) {
				throw new IOException("Failed to parse mapping resource: '" + file.getAbsolutePath() + "'", e);
			} finally {
				ErrorContext.instance().reset();
			}
		}

	}

	public void scan() throws IOException {
		if (!fileMapping.isEmpty()) {
			return;
		}
		for (String mapperLocation : mapperLocations) {
			Resource[] resources = getResource(mapperLocation);
			if (resources != null) {
				for (Resource resource : resources) {
					String multiKey = getValue(resource);
					fileMapping.put(resource.getFilename(), multiKey);
				}
			}
		}
	}

	private String getValue(Resource resource) throws IOException {
		String contentLength = String.valueOf((resource.contentLength()));
		String lastModified = String.valueOf((resource.lastModified()));
		return contentLength+lastModified;
	}

	public boolean isChanged() throws IOException {
		boolean isChanged = false;
		changeMappers.clear();
		for (String mapperLocation : mapperLocations) {
			Resource[] resources = getResource(mapperLocation);
			if (resources != null) {
				for (Resource resource : resources) {
					String name = resource.getFilename();
					String value = fileMapping.get(name);
					String multiKey = getValue(resource);
					if (!multiKey.equals(value)) {
						changeMappers.add(name);
						changeXmlFiles.add(resource.getFile());
						isChanged = true;
						fileMapping.put(name, multiKey);
					}
				}
			}
		}
		return isChanged;
	}

	private void mointerXmlChange() {
		service = new ScheduledThreadPoolExecutor(1,
				new BasicThreadFactory.Builder().namingPattern("example-schedule-pool-%d").daemon(true).build());
		service.scheduleAtFixedRate(new MointerMybatisXmlChangeTask(this, changeMappers), 5, 5, TimeUnit.SECONDS);
	}

	public void shutDownTask() {
		if (service != null) {
			service.shutdownNow();
		}
	}

	public void scanAndMointerXmlChange() throws Exception {
		this.scan();
		this.mointerXmlChange();
	}

}
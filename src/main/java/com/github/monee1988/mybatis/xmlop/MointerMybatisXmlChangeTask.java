package com.github.monee1988.mybatis.xmlop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动刷新 mybatis的xml任务
 * @author monee1988
 */
public class MointerMybatisXmlChangeTask implements Runnable {
	
	private final Logger logger = LoggerFactory.getLogger(getClass());  

	private MybatisXmlScanner scanner;
	
	private List<String> changeMapers = new ArrayList<>();

	private MointerMybatisXmlChangeTask() {
		super();
	}
	
	
	public MointerMybatisXmlChangeTask(MybatisXmlScanner scanner, List<String> changeMapers) {
		this();
		this.scanner = scanner;
		this.changeMapers = changeMapers;
	}

	@Override
	public void run() {
		try {
			if (scanner.isChanged()) {
				logger.info(changeMapers.toString()+"文件改变,重新加载.");
				scanner.reloadXml();
				logger.info("***Mapper.xml加载完毕");
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("mybatis xml reload error");
		}
	}


	public void setScanner(MybatisXmlScanner scanner) {
		this.scanner = scanner;
	}

	public void setChangeMapers(List<String> changeMapers) {
		this.changeMapers = changeMapers;
	}
	

}

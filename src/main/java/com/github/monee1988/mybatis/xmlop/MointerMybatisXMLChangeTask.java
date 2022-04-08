package com.github.monee1988.mybatis.xmlop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动刷新 mybatis的xml任务
 * @author monee1988
 */
public class MointerMybatisXMLChangeTask implements Runnable {
	
	private final Logger logger = LoggerFactory.getLogger(getClass());  

	private MybatisXMLScanner scanner;
	
	private List<String> changeMapers = new ArrayList<String>();

	private MointerMybatisXMLChangeTask() {
		super();
	}
	
	
	public MointerMybatisXMLChangeTask(MybatisXMLScanner scanner, List<String> changeMapers) {
		this();
		this.scanner = scanner;
		this.changeMapers = changeMapers;
	}


	public void run() {
		try {
			if (scanner.isChanged()) {
				logger.info(changeMapers.toString()+"文件改变,重新加载.");
				scanner.reloadXML();
				logger.info("***Mapper.xml加载完毕");
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("mybatis xml reload error");
		}
	}


	public void setScanner(MybatisXMLScanner scanner) {
		this.scanner = scanner;
	}

	public void setChangeMapers(List<String> changeMapers) {
		this.changeMapers = changeMapers;
	}
	

}

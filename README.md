## [mybatis-page](https://github.com/monee1988/mybatis-page)

mybais-page 是自定义的一个 mybatis 分页插件，方便系统集成，用户只需要集成到自己的系统中就可以实现自动分页功能。目前支持 Mysql、Oracle和SqlServer，当然用户也可以自己扩展自己需要的数据库分页。

### [中央库地址](https://search.maven.org/artifact/com.github.monee1988/mybatis-page/1.0.1-RELEASE/jar)
### [mvnrepository 地址](http://mvnrepository.com/artifact/com.github.monee1988/mybatis-page)
### maven 坐标 :

```
<dependency>
    <groupId>com.github.monee1988</groupId>
    <artifactId>mybatis-page</artifactId>
    <version>1.0.1-RELEASE</version>
</dependency>
```
### gradle 坐标 :
```
implementation 'com.github.monee1988:mybatis-page:1.0.1-RELEASE'
```
### 依賴插件版本
| 依赖               | 版本   | 
|------------------|------|
| mybatis          | 3.5.13 |
| mybatis-spring   | 3.0.2 |
| Spring Framework | 5.3.27 |
| slf4j | 2.0.7 |
| logback | 1.2.12 |

### 1 分页拦截器配置

#### 1.1 配置方式一：spring配置
```
<bean id="sqlSessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
    <property name="dataSource" ref="dataSource" />
    <!-- <property name="configuration" value="classpath:mybatis-config.xml"/ -->
    <!-- 自动扫描entity目录, 省掉Configuration.xml里的手工配置 -->
    <property name="mapperLocations" value="classpath:mappers/*/*.xml" />
    <property name="plugins">
        <array>
            <!-- 定义加入mybatis-page分页拦截器 -->
            <bean class="com.github.monee1988.mybatis.MybatisInterceptor">
            <!-- 当前分页类型选择Mysql -->
            <property name="dialectClass" value="com.github.monee1988.mybatis.dialect.support.MySqlDialect"/>
            <!-- 当前分页类型选择Oracle -->
            <!--<property name="dialectClass" value="com.github.monee1988.mybatis.dialect.support.OracleDialect"/>-->
            <!-- 当前分页类型选择SqlServer -->
            <!--<property name="dialectClass" value="com.github.monee1988.mybatis.dialect.support.SqlServerDialect"/>-->
            </bean>
        </array>
    </property>
</bean>
```
#### 1.2 配置方式二：mybatis-config.xml配置

```
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE configuration PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-config.dtd">
<configuration>
    <settings>
        <setting name="cacheEnabled" value="true" />
		......
    </settings>
   <plugins>
        <plugin interceptor="com.github.monee1988.mybatis.MybatisInterceptor">
            <property name="dialectClassName" value="com.github.monee1988.mybatis.dialect.support.MySqlDialect"/>
        ~~</plugin>~~
    </plugins>
</configuration>
```



### 2.  修改***Mapper.xml中sql语句自动刷新功能

为方便开发加入了 ***Mapper.xml 的自动刷新功能，可以大大缩减开发效率，不用修改 sql 语句后频繁的重启服务。
#### 2.1 用法：
只需要在 spring 配置文件中加入以下代码(目前只适合 xml 文件在 mappers 文件夹下的情况)，


```
<!-- 动态加载sqlSessionFactory 特定的XML -->
<bean class="com.github.monee1988.mybatis.MybatisMapperDynamicLoader">
    <property name="sqlSessionFactory" ref="sqlSessionFactory"/>
    <property name="mapperDynamicLoader" value="true"/>	
    <property name="mapperLocations">
        <array>
            <value>classpath:mappers/test1/*.xml</value>
        </array>
    </property>
</bean>
<!-- 动态加载sqlSessionFactory2 特定的XML -->
<bean class="com.github.monee1988.mybatis.MybatisMapperDynamicLoader">
    <property name="sqlSessionFactory" ref="sqlSessionFactory2"/>
    <property name="mapperDynamicLoader" value="true"/>	
    <property name="mapperLocations">
        <array>
            <value>classpath:mappers/test2/read/*.xml</value>
        </array>
    </property>
</bean>
```

### 3. 分页用法
```
返回类型 Page<T>
```

entity 加入分页对象
```
@Data
@EqualsAndHashCode(callSuper = true)
public class Country{

    private String code;

    private String name;

    private Page<Country> page;
		
}
```

Controller 示例代码

```
@RequestMapping(value = {"page"} ,method = RequestMethod.GET)
public String findPageList(ModelMap modelMap,@RequestParam(defaultValue = "1",required = false) Integer pageNo,@RequestParam(defaultValue = "20",required = false)Integer pageSize){

    Page<Country> result = testService.findPage(new Country(), new Page<Test>(pageNo,pageSize));
    modelMap.put("message", result );
    
    return "pageIndex";
}
```

Service 示例代码

```
public Page<Country> findPage(Country country, Page<Country> page) {
		
    test.setPage(page);
    page.setList(testdao.findList(country));

    return page;
}
```

XML文件事例(事例没有按标准写完整的带字段的SQL语句，开发中不建议此写法)

```
<select id="findList" resultType="Country">
    SELECT code,name,.... FROM country
</select>
```

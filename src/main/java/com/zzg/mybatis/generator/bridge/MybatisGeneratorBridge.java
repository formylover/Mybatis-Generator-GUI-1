package com.zzg.mybatis.generator.bridge;

import com.zzg.mybatis.generator.model.DatabaseConfig;
import com.zzg.mybatis.generator.model.DbType;
import com.zzg.mybatis.generator.model.GeneratorConfig;
import com.zzg.mybatis.generator.plugins.DbRemarksCommentGenerator;
import com.zzg.mybatis.generator.util.ConfigHelper;
import com.zzg.mybatis.generator.util.DbUtil;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.generator.api.MyBatisGenerator;
import org.mybatis.generator.api.ProgressCallback;
import org.mybatis.generator.api.ShellCallback;
import org.mybatis.generator.config.*;
import org.mybatis.generator.internal.DefaultShellCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The bridge between GUI and the mybatis generator. All the operation to  mybatis generator should proceed through this
 * class
 * <p>
 * Created by Owen on 6/30/16.
 */
public class MybatisGeneratorBridge {

	private static final Logger LOGGER = LoggerFactory.getLogger(MybatisGeneratorBridge.class);

	private GeneratorConfig generatorConfig;

	private DatabaseConfig selectedDatabaseConfig;

	private ProgressCallback progressCallback;

	private List<IgnoredColumn> ignoredColumns;

	private List<ColumnOverride> columnOverrides;

	public MybatisGeneratorBridge() {
	}

	public void setGeneratorConfig(GeneratorConfig generatorConfig) {
		this.generatorConfig = generatorConfig;
	}

	public void setDatabaseConfig(DatabaseConfig databaseConfig) {
		this.selectedDatabaseConfig = databaseConfig;
	}

	public void setProgressCallback(ProgressCallback progressCallback) {
		this.progressCallback = progressCallback;
	}

	public void setIgnoredColumns(List<IgnoredColumn> ignoredColumns) {
		this.ignoredColumns = ignoredColumns;
	}

	public void setColumnOverrides(List<ColumnOverride> columnOverrides) {
		this.columnOverrides = columnOverrides;
	}


	public void generate() throws Exception {
		Configuration configuration = new Configuration();

		// classPathEntry
		String connectorLibPath = ConfigHelper.findConnectorLibPath(selectedDatabaseConfig.getDbType());
		LOGGER.info("connectorLibPath: {}", connectorLibPath);
		configuration.addClasspathEntry(connectorLibPath);

		// context
		Context context = new Context(ModelType.FLAT);
		context.setId("hello world");
		context.setTargetRuntime("MyBatis3Simple");
		context.addProperty("javaFileEncoding", "UTF-8");
		context.addProperty("javaFormatter", "org.mybatis.generator.api.dom.DefaultJavaFormatter");
		context.addProperty("xmlFormatter", "org.mybatis.generator.api.dom.DefaultXmlFormatter");

		// Table configuration
		TableConfiguration tableConfig = new TableConfiguration(context);
		tableConfig.setTableName(generatorConfig.getTableName());
		tableConfig.setDomainObjectName(generatorConfig.getDomainObjectName());

		// 针对 postgresql 单独配置
		if (DbType.valueOf(selectedDatabaseConfig.getDbType()).getDriverClass().equals("org.postgresql.Driver")) {
			tableConfig.setDelimitIdentifiers(true);
		}

		//添加GeneratedKey主键生成
		if (StringUtils.isNoneEmpty(generatorConfig.getGenerateKeys())) {
			tableConfig.setGeneratedKey(new GeneratedKey(generatorConfig.getGenerateKeys(), selectedDatabaseConfig.getDbType(), true, null));
		}
		// 设置Mapper名称
		if (generatorConfig.getMapperName() != null) {
			tableConfig.setMapperName(generatorConfig.getMapperName());
		}

		// add ignore columns
		if (ignoredColumns != null) {
			ignoredColumns.forEach(tableConfig::addIgnoredColumn);
		}
		// add columns override
		if (columnOverrides != null) {
			columnOverrides.forEach(tableConfig::addColumnOverride);
		}
		// 使用列名
		if (generatorConfig.isUseActualColumnNames()) {
			tableConfig.addProperty("useActualColumnNames", "true");
		}

		// jdbcConnection
		JDBCConnectionConfiguration jdbcConfig = new JDBCConnectionConfiguration();
		String driverClass = DbType.valueOf(selectedDatabaseConfig.getDbType()).getDriverClass();
		jdbcConfig.setDriverClass(driverClass);
		jdbcConfig.setConnectionURL(DbUtil.getConnectionUrlWithSchema(selectedDatabaseConfig));
		jdbcConfig.setUserId(selectedDatabaseConfig.getUsername());
		jdbcConfig.setPassword(selectedDatabaseConfig.getPassword());

		// java model
		JavaModelGeneratorConfiguration modelConfig = new JavaModelGeneratorConfiguration();
		modelConfig.setTargetPackage(generatorConfig.getModelPackage());
		modelConfig.setTargetProject(generatorConfig.getProjectFolder() + "/" + generatorConfig.getModelPackageTargetFolder());

		// XML Mapper
		SqlMapGeneratorConfiguration mapperConfig = new SqlMapGeneratorConfiguration();
		mapperConfig.setTargetPackage(generatorConfig.getMappingXMLPackage());
		mapperConfig.setTargetProject(generatorConfig.getProjectFolder() + "/" + generatorConfig.getMappingXMLTargetFolder());

		// Mapper Interface
		JavaClientGeneratorConfiguration daoConfig = new JavaClientGeneratorConfiguration();
		daoConfig.setConfigurationType("XMLMAPPER");
		daoConfig.setTargetPackage(generatorConfig.getDaoPackage());
		daoConfig.setTargetProject(generatorConfig.getProjectFolder() + "/" + generatorConfig.getDaoTargetFolder());


		context.setJdbcConnectionConfiguration(jdbcConfig);
		context.setJavaModelGeneratorConfiguration(modelConfig);
		context.setSqlMapGeneratorConfiguration(mapperConfig);
		context.setJavaClientGeneratorConfiguration(daoConfig);
		context.addTableConfiguration(tableConfig);

		// Comment & jpaAnnotation
		CommentGeneratorConfiguration commentConfig = new CommentGeneratorConfiguration();
		commentConfig.setConfigurationType(DbRemarksCommentGenerator.class.getName());
		if (generatorConfig.isComment()) {
			commentConfig.addProperty("columnRemarks", "true");
		}
		if (generatorConfig.isJpaAnnotation()) {
			commentConfig.addProperty("annotations", "true");
		}
		context.setCommentGeneratorConfiguration(commentConfig);

		// 使用lombok注解
		if (generatorConfig.enableLombokAnnotation()) {
			PluginConfiguration lombokPluginConfiguration = new PluginConfiguration();
			lombokPluginConfiguration.setConfigurationType("com.zzg.mybatis.generator.plugins.LombokPlugin");
			if (generatorConfig.isToString() &&
					generatorConfig.isHashCodeAndEquals() &&
						generatorConfig.isGetterAndSetter()) { // 使用@Data
				lombokPluginConfiguration.addProperty("data", "true");
			}else{
				if (generatorConfig.isGetterAndSetter()) {
					lombokPluginConfiguration.addProperty("getter", "true");
					lombokPluginConfiguration.addProperty("setter", "true");
				}
				if (generatorConfig.isToString()) {
					lombokPluginConfiguration.addProperty("toString", "true");
				}
				if (generatorConfig.isHashCodeAndEquals()) {
					lombokPluginConfiguration.addProperty("equalsAndHashCode", "true");
				}
			}
			context.addPluginConfiguration(lombokPluginConfiguration);
		} else { // 不使用lombok注解

			if (generatorConfig.isToString()) {
				PluginConfiguration toStringPluginConfig = new PluginConfiguration();
				toStringPluginConfig.setConfigurationType("org.mybatis.generator.plugins.ToStringPlugin");
				toStringPluginConfig.addProperty("type", "org.mybatis.generator.plugins.ToStringPlugin");
				context.addPluginConfiguration(toStringPluginConfig);
			}
			if (generatorConfig.isHashCodeAndEquals()) {
				PluginConfiguration hashCodeAndEqualsPluginConfig = new PluginConfiguration();
				hashCodeAndEqualsPluginConfig.setConfigurationType("org.mybatis.generator.plugins.EqualsHashCodePlugin");
				hashCodeAndEqualsPluginConfig.addProperty("type", "org.mybatis.generator.plugins.EqualsHashCodePlugin");
				context.addPluginConfiguration(hashCodeAndEqualsPluginConfig);
			}
		}


		configuration.addContext(context);

		List<String> warnings = new ArrayList<>();
		Set<String> fullyQualifiedTables = new HashSet<>();
		Set<String> contexts = new HashSet<>();
		// override=true
		ShellCallback shellCallback = new DefaultShellCallback(true);
		MyBatisGenerator myBatisGenerator = new MyBatisGenerator(configuration, shellCallback, warnings);
		myBatisGenerator.generate(progressCallback, contexts, fullyQualifiedTables);
	}


}

/*
 * Copyright 2017-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example.springdata.jdbc.basics.aggregate;

import java.sql.Clob;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.jdbc.core.mapping.event.BeforeSaveEvent;
import org.springframework.data.jdbc.core.mapping.ConversionCustomizer;
import org.springframework.data.jdbc.core.mapping.JdbcPersistentProperty;
import org.springframework.data.jdbc.core.mapping.NamingStrategy;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.lang.Nullable;

/**
 * @author Jens Schauder
 */
@Configuration
@EnableJdbcRepositories
public class AggregateConfiguration {

	final AtomicInteger id = new AtomicInteger(0);

	@Bean
	public ApplicationListener<?> idSetting() {

		return (ApplicationListener<BeforeSaveEvent>) event -> {

			if (event.getEntity() instanceof LegoSet) {
				setIds((LegoSet) event.getEntity());
			}
		};
	}

	private void setIds(LegoSet legoSet) {

		if (legoSet.getId() == 0) {
			legoSet.setId(id.incrementAndGet());
		}

		Manual manual = legoSet.getManual();

		if (manual != null) {
			manual.setId((long) legoSet.getId());
		}
	}

	@Bean
	public NamingStrategy namingStrategy() {

		Map<String, String> columnAliases = new HashMap<String, String>();
		columnAliases.put("lego_set.int_maximum_age", "max_age");
		columnAliases.put("lego_set.int_minimum_age", "min_age");

		Map<String, String> reverseColumnAliases = new HashMap<String, String>();
		reverseColumnAliases.put("manual", "handbuch_id");

		Map<String, String> keyColumnAliases = new HashMap<String, String>();
		keyColumnAliases.put("models", "name");

		return new NamingStrategy() {

			@Override
			public String getColumnName(JdbcPersistentProperty property) {

				String defaultName = NamingStrategy.super.getColumnName(property);
				String key = getTableName(property.getOwner().getType()) + "." + defaultName;
				return columnAliases.computeIfAbsent(key, __ -> defaultName);
			}

			@Override
			public String getReverseColumnName(JdbcPersistentProperty property) {
				return reverseColumnAliases.computeIfAbsent(property.getName(),
						__ -> NamingStrategy.super.getReverseColumnName(property));
			}

			@Override
			public String getKeyColumn(JdbcPersistentProperty property) {
				return keyColumnAliases.computeIfAbsent(property.getName(), __ -> NamingStrategy.super.getKeyColumn(property));
			}
		};
	}

	@Bean
	public ConversionCustomizer conversionCustomizer() {

		return conversions -> conversions.addConverter(new Converter<Clob, String>() {

			@Nullable
			@Override
			public String convert(Clob clob) {

				try {

					return Math.toIntExact(clob.length()) == 0 //
							? "" //
							: clob.getSubString(1, Math.toIntExact(clob.length()));

				} catch (SQLException e) {
					throw new IllegalStateException("Failed to convert CLOB to String.", e);
				}
			}
		});
	}
}

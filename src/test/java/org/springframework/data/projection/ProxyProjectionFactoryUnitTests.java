/*
 * Copyright 2014-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.projection;

import static org.assertj.core.api.Assertions.*;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.TargetClassAware;
import org.springframework.aop.framework.Advised;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link ProxyProjectionFactory}.
 *
 * @author Oliver Gierke
 */
public class ProxyProjectionFactoryUnitTests {

	ProjectionFactory factory = new ProxyProjectionFactory();

	@Test
	@SuppressWarnings("null")
	// DATACMNS-630
	public void rejectsNullProjectionType() {
		assertThatIllegalArgumentException().isThrownBy(() -> factory.createProjection(null));
	}

	@Test
	@SuppressWarnings("null")
	// DATACMNS-630
	public void rejectsNullProjectionTypeWithSource() {
		assertThatIllegalArgumentException().isThrownBy(() -> factory.createProjection(null, new Object()));
	}

	@Test // DATACMNS-630
	public void returnsNullForNullSource() {
		assertThat(factory.createNullableProjection(CustomerExcerpt.class, null)).isNull();
	}

	@Test // DATAREST-221, DATACMNS-630
	public void createsProjectingProxy() {

		Customer customer = new Customer();
		customer.firstname = "Dave";
		customer.lastname = "Matthews";

		customer.address = new Address();
		customer.address.city = "New York";
		customer.address.zipCode = "ZIP";

		CustomerExcerpt excerpt = factory.createProjection(CustomerExcerpt.class, customer);

		assertThat(excerpt.getFirstname()).isEqualTo("Dave");
		assertThat(excerpt.getAddress().getZipCode()).isEqualTo("ZIP");
	}

	@Test // DATAREST-221, DATACMNS-630
	public void proxyExposesTargetClassAware() {

		CustomerExcerpt proxy = factory.createProjection(CustomerExcerpt.class);

		assertThat(proxy).isInstanceOf(TargetClassAware.class);
		assertThat(((TargetClassAware) proxy).getTargetClass()).isEqualTo(HashMap.class);
	}

	@Test // DATAREST-221, DATACMNS-630
	public void rejectsNonInterfacesAsProjectionTarget() {
		assertThatIllegalArgumentException().isThrownBy(() -> factory.createProjection(Object.class, new Object()));
	}

	@Test // DATACMNS-630
	public void createsMapBasedProxyFromSource() {

		HashMap<String, Object> addressSource = new HashMap<>();
		addressSource.put("zipCode", "ZIP");
		addressSource.put("city", "NewYork");

		Map<String, Object> source = new HashMap<>();
		source.put("firstname", "Dave");
		source.put("lastname", "Matthews");
		source.put("address", addressSource);

		CustomerExcerpt projection = factory.createProjection(CustomerExcerpt.class, source);

		assertThat(projection.getFirstname()).isEqualTo("Dave");

		AddressExcerpt address = projection.getAddress();
		assertThat(address).isNotNull();
		assertThat(address.getZipCode()).isEqualTo("ZIP");
	}

	@Test // DATACMNS-630
	public void createsEmptyMapBasedProxy() {

		CustomerProxy proxy = factory.createProjection(CustomerProxy.class);

		assertThat(proxy).isNotNull();

		proxy.setFirstname("Dave");
		assertThat(proxy.getFirstname()).isEqualTo("Dave");
	}

	@Test // DATACMNS-630
	public void returnsAllPropertiesAsInputProperties() {

		ProjectionInformation projectionInformation = factory.getProjectionInformation(CustomerExcerpt.class);
		List<PropertyDescriptor> result = projectionInformation.getInputProperties();

		assertThat(result).hasSize(6);
	}

	@Test // DATACMNS-655
	public void invokesDefaultMethodOnProxy() {

		CustomerExcerpt excerpt = factory.createProjection(CustomerExcerpt.class);

		Advised advised = (Advised) ReflectionTestUtils.getField(Proxy.getInvocationHandler(excerpt), "advised");
		Advisor[] advisors = advised.getAdvisors();

		assertThat(advisors.length).isGreaterThan(0);
		assertThat(advisors[0].getAdvice()).isInstanceOf(DefaultMethodInvokingMethodInterceptor.class);
	}

	@Test // DATACMNS-648
	public void exposesProxyTarget() {

		CustomerExcerpt excerpt = factory.createProjection(CustomerExcerpt.class);

		assertThat(excerpt).isInstanceOf(TargetAware.class);
		assertThat(((TargetAware) excerpt).getTarget()).isInstanceOf(Map.class);
	}

	@Test // DATACMNS-722
	public void doesNotProjectPrimitiveArray() {

		Customer customer = new Customer();
		customer.picture = "binarydata".getBytes();

		CustomerExcerpt excerpt = factory.createProjection(CustomerExcerpt.class, customer);

		assertThat(excerpt.getPicture()).isEqualTo(customer.picture);
	}

	@Test // DATACMNS-722
	public void projectsNonPrimitiveArray() {

		Address address = new Address();
		address.city = "New York";
		address.zipCode = "ZIP";

		Customer customer = new Customer();
		customer.shippingAddresses = new Address[] { address };

		CustomerExcerpt excerpt = factory.createProjection(CustomerExcerpt.class, customer);

		assertThat(excerpt.getShippingAddresses()).hasSize(1);
	}

	@Test // DATACMNS-782
	public void convertsPrimitiveValues() {

		Customer customer = new Customer();
		customer.id = 1L;

		CustomerExcerpt excerpt = factory.createProjection(CustomerExcerpt.class, customer);

		assertThat(excerpt.getId()).isEqualTo(customer.id.toString());
	}

	@Test // DATACMNS-89
	public void exposesProjectionInformationCorrectly() {

		ProjectionInformation information = factory.getProjectionInformation(CustomerExcerpt.class);

		assertThat(information.getType()).isEqualTo(CustomerExcerpt.class);
		assertThat(information.isClosed()).isTrue();
	}

	@Test // DATACMNS-829
	public void projectsMapOfStringToObjectCorrectly() {

		Customer customer = new Customer();
		customer.data = Collections.singletonMap("key", null);

		Map<String, Object> data = factory.createProjection(CustomerExcerpt.class, customer).getData();

		assertThat(data).isNotNull();
		assertThat(data.containsKey("key")).isTrue();
		assertThat(data.get("key")).isNull();
	}

	@Test // DATACMNS-1121
	public void doesNotCreateWrappingProxyIfTargetImplementsProjectionInterface() {

		Customer customer = new Customer();

		assertThat(factory.createProjection(Contact.class, customer)).isSameAs(customer);
	}

	interface Contact {}

	static class Customer implements Contact {

		public Long id;
		public String firstname, lastname;
		public Address address;
		public byte[] picture;
		public Address[] shippingAddresses;
		public Map<String, Object> data;
	}

	static class Address {

		public String zipCode, city;
	}

	interface CustomerExcerpt {

		String getId();

		String getFirstname();

		AddressExcerpt getAddress();

		AddressExcerpt[] getShippingAddresses();

		byte[] getPicture();

		Map<String, Object> getData();
	}

	interface AddressExcerpt {

		String getZipCode();
	}

	interface CustomerProxy {

		String getFirstname();

		void setFirstname(String firstname);
	}
}

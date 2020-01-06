/*
 *
 *  Copyright 2017-2019 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

package springfox.test.contract.swaggertests

import com.fasterxml.classmate.TypeResolver
import groovy.json.JsonSlurper
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.test.context.ContextConfiguration
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import springfox.documentation.schema.AlternateTypeRuleConvention
import springfox.documentation.spring.web.plugins.JacksonSerializerConvention

import static org.skyscreamer.jsonassert.JSONCompareMode.*
import static org.springframework.boot.test.context.SpringBootTest.*

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = Config)
class FunctionContractSpec extends Specification implements FileAccess {

  @Shared
  def http = new TestRestTemplate()

  @Value('${local.server.port}')
  int port

  @Unroll
  def 'should honor swagger v2 resource listing #groupName'() {
    given:
    RequestEntity<Void> request = RequestEntity.get(
        new URI("http://localhost:$port/v2/api-docs?group=$groupName"))
        .accept(MediaType.APPLICATION_JSON)
        .build()
    String contract = fileContents("/contract/swagger2/$contractFile")

    when:
    def response = http.exchange(request, String)
    then:
    String raw = response.body
    response.statusCode == HttpStatus.OK

    def withPortReplaced = contract.replaceAll("__PORT__", "$port")
    maybeWriteToFile(
        "/contract/swagger2/$contractFile",
        raw.replace("localhost:$port", "localhost:__PORT__"))
    JSONAssert.assertEquals(withPortReplaced, raw, NON_EXTENSIBLE)

    where:
    contractFile                                                  | groupName
    'swagger.json'                                                | 'petstore'
    'swaggerTemplated.json'                                       | 'petstoreTemplated'
    'declaration-groovy-service.json'                             | 'groovyService'
  }

  def "should list swagger resources for swagger 2.0"() {
    given:
    def http = new TestRestTemplate()
    RequestEntity<Void> request = RequestEntity.get(new URI("http://localhost:$port/swagger-resources"))
        .accept(MediaType.APPLICATION_JSON)
        .build()

    when:
    def response = http.exchange(request, String)
    def slurper = new JsonSlurper()
    def result = slurper.parseText(response.body)

    then:
    result.find {
      it.name == 'petstore' &&
          it.url == '/v2/api-docs?group=petstore' &&
          it.swaggerVersion == '2.0'
    }
    result.find {
      it.name == 'petstoreTemplated' &&
          it.url == '/v2/api-docs?group=petstoreTemplated' &&
          it.swaggerVersion == '2.0'
    }
    result.find {
      it.name == 'groovyService' &&
          it.url == '/v2/api-docs?group=groovyService' &&
          it.swaggerVersion == '2.0'
    }
  }

  @Configuration
  @ComponentScan([
      "springfox.documentation.spring.web.dummy.controllers",
      "springfox.test.contract.swagger",
      "springfox.petstore.webflux.controller"
  ])
  @Import([
      SecuritySupport,
      Swagger2TestConfig])
  static class Config {

    // tag::alternate-type-rule-convention[]
    @Bean
    AlternateTypeRuleConvention jacksonSerializerConvention(TypeResolver resolver) {
      new JacksonSerializerConvention(resolver, "springfox.documentation.spring.web.dummy.models")
    }
    // tag::alternate-type-rule-convention[]
  }
}
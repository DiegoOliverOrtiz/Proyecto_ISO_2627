package es.uclm.esibuy.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.mongodb.client.MongoClient;

@SpringBootTest(properties = {
		"TOTP_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
		"server.ssl.enabled=false",
		"TOTP_MIGRATE_KEY=false"
})
class BackendApplicationTests {

	@MockitoBean
	private MongoClient mongoClient;

	@Test
	void contextLoads() {
	}

}

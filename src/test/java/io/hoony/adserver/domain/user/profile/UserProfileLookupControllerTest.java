package io.hoony.adserver.domain.user.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProfileLookupController.class)
@Import(TestUserProfileClientConfig.class)
class UserProfileLookupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("존재하는 유저 프로필은 200 응답으로 반환한다.")
    void returnsProfileWhenUserExists() throws Exception {
        mockMvc.perform(get("/internal/dmp/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("1"))
                .andExpect(jsonPath("$.gender").value("M"))
                .andExpect(jsonPath("$.locationId").value("1:11"))
                .andExpect(jsonPath("$.age").value(29))
                .andExpect(jsonPath("$.tags[0]").value("fashion"));
    }

    @Test
    @DisplayName("존재하지 않는 유저 프로필은 404를 반환한다.")
    void returnsNotFoundWhenUserDoesNotExist() throws Exception {
        mockMvc.perform(get("/internal/dmp/users/missing"))
                .andExpect(status().isNotFound());
    }
}

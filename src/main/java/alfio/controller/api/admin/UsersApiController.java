/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.controller.api.admin;

import alfio.manager.user.UserManager;
import alfio.model.modification.OrganizationModification;
import alfio.model.modification.UserModification;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.model.user.UserWithPassword;
import alfio.util.ImageUtil;
import alfio.util.Json;
import alfio.util.ValidationResult;
import lombok.experimental.Delegate;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.web.bind.annotation.RequestMethod.*;

@RestController
@RequestMapping("/admin/api")
@Log4j2
public class UsersApiController {

    private static final String OK = "OK";
    private static final String USER_WITH_PASSWORD_KEY = "USER_WITH_PASSWORD";
    private final UserManager userManager;


    @Autowired
    public UsersApiController(UserManager userManager) {
        this.userManager = userManager;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public String unhandledException(Exception e) {
        log.error("unhandled exception", e);
        return e.getMessage();
    }

    @RequestMapping(value = "/roles", method = GET)
    public Collection<RoleDescriptor> getAllRoles(Principal principal) {
        return userManager.getAvailableRoles(principal.getName()).stream().map(RoleDescriptor::new).collect(Collectors.toList());
    }

    @RequestMapping(value = "/organizations", method = GET)
    @ResponseStatus(HttpStatus.OK)
    public List<Organization> getAllOrganizations(Principal principal) {
        return userManager.findUserOrganizations(principal.getName());
    }

    @RequestMapping(value = "/organizations/{id}", method = GET)
    public Organization getOrganization(@PathVariable("id") int id, Principal principal) {
        return userManager.findOrganizationById(id, principal.getName());
    }

    @RequestMapping(value = "/users", method = GET)
    public List<User> getAllUsers(Principal principal) {
        return userManager.findAllUsers(principal.getName());
    }


    @RequestMapping(value = "/organizations/new", method = POST)
    public String insertOrganization(@RequestBody OrganizationModification om) {
        userManager.createOrganization(om.getName(), om.getDescription(), om.getEmail());
        return OK;
    }

    @RequestMapping(value = "/organizations/update", method = POST)
    public String updateOrganization(@RequestBody OrganizationModification om) {
        userManager.updateOrganization(om.getId(), om.getName(), om.getEmail(), om.getDescription());
        return OK;
    }

    @RequestMapping(value = "/organizations/check", method = POST)
    public ValidationResult validateOrganization(@RequestBody OrganizationModification om) {
        return userManager.validateOrganization(om.getId(), om.getName(), om.getEmail(), om.getDescription());
    }

    @RequestMapping(value = "/users/check", method = POST)
    public ValidationResult validateUser(@RequestBody UserModification userModification) {
        return userManager.validateUser(userModification.getId(), userModification.getUsername(),
                userModification.getOrganizationId(), userModification.getRole(), userModification.getFirstName(),
                userModification.getLastName(), userModification.getEmailAddress());
    }

    @RequestMapping(value = "/users/edit", method = POST)
    public String editUser(@RequestBody UserModification userModification, Principal principal) {
        userManager.editUser(userModification.getId(), userModification.getOrganizationId(), userModification.getUsername(), userModification.getFirstName(), userModification.getLastName(), userModification.getEmailAddress(), Role.valueOf(userModification.getRole()), principal.getName());
        return OK;
    }

    @RequestMapping(value = "/users/new", method = POST)
    public UserWithPassword insertUser(@RequestBody UserModification userModification, HttpSession session, Principal principal) {
        Role requested = Role.valueOf(userModification.getRole());
        Validate.isTrue(userManager.getAvailableRoles(principal.getName()).stream().anyMatch(requested::equals), String.format("Requested role %s is not available for current user", userModification.getRole()));
        UserWithPassword userWithPassword = userManager.insertUser(userModification.getOrganizationId(), userModification.getUsername(), userModification.getFirstName(), userModification.getLastName(), userModification.getEmailAddress(), requested);
        storePasswordImage(session, userWithPassword);
        return userWithPassword;
    }

    @RequestMapping(value = "/users/{identifier}.png", method = GET)
    public void loadUserImage(@PathVariable("identifier") String identifier, @RequestParam("baseUrl") String baseUrl, HttpSession session, HttpServletResponse response) throws IOException {
        Optional<UserWithPassword> optional = Optional.ofNullable((UserWithPassword) session.getAttribute(USER_WITH_PASSWORD_KEY))
                                                       .filter(a -> identifier.equals(a.getUniqueId()));
        if(!optional.isPresent()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        UserWithPassword userWithPassword = optional.get();
        response.setContentType("image/png");

        Map<String, Object> info = new HashMap<>();
        info.put("username", userWithPassword.getUsername());
        info.put("password", userWithPassword.getPassword());
        info.put("baseUrl", baseUrl);
        //
        response.getOutputStream().write(ImageUtil.createQRCode(Json.GSON.toJson(info)));
    }


    @RequestMapping(value = "/users/{id}", method = DELETE)
    public String deleteUser(@PathVariable("id") int userId, Principal principal) {
        userManager.deleteUser(userId, principal.getName());
        return OK;
    }

    @RequestMapping(value = "/users/{id}", method = GET)
    public UserModification loadUser(@PathVariable("id") int userId) {
        User user = userManager.findUser(userId);
        List<Organization> userOrganizations = userManager.findUserOrganizations(user);
        return new UserModification(user.getId(), userOrganizations.get(0).getId(), userManager.getUserRole(user).name(), user.getUsername(), user.getFirstName(), user.getLastName(), user.getEmailAddress());
    }

    @RequestMapping(value = "/users/{id}/reset-password", method = PUT)
    public UserWithPassword resetPassword(@PathVariable("id") int userId, HttpSession session) {
        UserWithPassword userWithPassword = userManager.resetPassword(userId);
        storePasswordImage(session, userWithPassword);
        return userWithPassword;
    }

    private void storePasswordImage(HttpSession session, UserWithPassword userWithPassword) {
        session.setAttribute(USER_WITH_PASSWORD_KEY, userWithPassword);
    }

    static final class RoleDescriptor {
        private final Role role;

        RoleDescriptor(Role role) {
            this.role = role;
        }

        public String getRole() {
            return role.name();
        }

        public String getDescription() {
            return role.getDescription();
        }
    }
}

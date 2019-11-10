package com.icthh.xm.service.permission;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.icthh.xm.domain.permission.Permission;
import com.icthh.xm.domain.permission.Privilege;
import com.icthh.xm.domain.permission.Role;
import com.icthh.xm.domain.permission.dto.PermissionDTO;
import com.icthh.xm.domain.permission.dto.PermissionMatrixDTO;
import com.icthh.xm.domain.permission.dto.RoleDTO;
import com.icthh.xm.domain.permission.dto.RoleMatrixDTO;
import com.icthh.xm.domain.permission.mapper.PermissionDomainMapper;
import com.icthh.xm.domain.permission.mapper.PermissionMapper;
import com.icthh.xm.domain.permission.mapper.PrivilegeMapper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.icthh.xm.domain.permission.dto.PermissionType.SYSTEM;
import static com.icthh.xm.domain.permission.dto.PermissionType.TENANT;

public class TenantRoleService {

    private static final String API = "/api";

    private static final String EMPTY_YAML = "---";

    private static final String CUSTOM_PRIVILEGES_PATH = "/config/tenants/{tenantName}/custom-privileges.yml";
    private static final String ROLE_SPEC_PATH = "/config/tenants/{tenantName}/custom-privileges.yml";
    private static final String PRIVILEGES_PATH = "/config/tenants/privileges.yml";
    private static final String PERMISSIONS_PATH = "/config/tenants/{tenantName}/permissions.yml";
    private static final String ROLES_PATH = "/config/tenants/{tenantName}/roles.yml";
    private static final String ENV_PATH = "/config/tenants/environments.yml";

    private ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public Map<String, Role> getRoles() throws IOException {
        TreeMap<String, Role> roles = getConfig(ROLE_SPEC_PATH, new TypeReference<TreeMap<String, Role>>() {});
        return roles != null ? roles : new TreeMap<>();
    }

    private Map<String, Map<String, Set<Permission>>> getPermissions() throws IOException {
        Map<String, Map<String, Set<Permission>>> permissions = getConfig(
            PERMISSIONS_PATH,
            new TypeReference<TreeMap<String, TreeMap<String, TreeSet<Permission>>>>() {
            });
        return permissions != null ? permissions : new TreeMap<>();
    }

    private Map<String, Set<Privilege>> getPrivileges() {
        String privilegesFile = getConfigContent(PRIVILEGES_PATH).orElse("");
        return StringUtils.isBlank(privilegesFile) ? new TreeMap<>() : new PrivilegeMapper()
                        .ymlToPrivileges(privilegesFile);
    }

    private Map<String, Set<Privilege>> getCustomPrivileges() {
        String tenant = "tenant name"; // TODO;
        String path = CUSTOM_PRIVILEGES_PATH;
        String privilegesFile = getConfigContent(path.replace("{tenantName}", tenant)).orElse("");
        return StringUtils.isBlank(privilegesFile) ? new TreeMap<>() : new PrivilegeMapper()
            .ymlToPrivileges(privilegesFile);
    }

    public void addRole(RoleDTO roleDto) throws IOException {


        Map<String, Role> roles = getRoles();

        if (null != roles.get(roleDto.getRoleKey())) {
            throw new RuntimeException("Role already exists");
        }

        Role role = new Role(roleDto.getRoleKey());
        role.setDescription(roleDto.getDescription());
        role.setCreatedBy("TODO");
        role.setCreatedDate(Instant.now().toString());
        role.setUpdatedBy("TBD");
        role.setUpdatedDate(roleDto.getUpdatedDate());
        roles.put(roleDto.getRoleKey(), role);

        updateRoles(mapper.writeValueAsString(roles));

        Map<String, Map<String, Set<Permission>>> permissions = getPermissions();
        if (StringUtils.isBlank(roleDto.getBasedOn())) {
            enrichExistingPermissions(permissions, roleDto.getRoleKey());
        } else {
            enrichExistingPermissions(permissions, roleDto.getRoleKey(), roleDto.getBasedOn());
        }

        updatePermissions(mapper.writeValueAsString(permissions));
    }

    public void updateRole(RoleDTO roleDto) throws IOException {
        Map<String, Role> roles = getRoles();
        Role role = roles.get(roleDto.getRoleKey());
        if (role == null) {
            throw new RuntimeException("Role doesn't exist");
        }
        // role updating
        role.setDescription(roleDto.getDescription());
        role.setUpdatedBy("rbd");
        role.setUpdatedDate(Instant.now().toString());
        roles.put(roleDto.getRoleKey(), role);
        updateRoles(mapper.writeValueAsString(roles));

        if (CollectionUtils.isEmpty(roleDto.getPermissions())) {
            return;
        }
        // permission updating
        Map<String, Map<String, Set<Permission>>> existingPermissions = getPermissions();
        enrichExistingPermissions(existingPermissions, roleDto.getPermissions());

        updatePermissions(mapper.writeValueAsString(existingPermissions));
    }

    public List<PermissionDTO> getRolePermissions(String roleKey) {
        String permissionsFile = getConfigContent(PERMISSIONS_PATH).orElse("");

        if (StringUtils.isBlank(permissionsFile)) {
            return Collections.emptyList();
        }

        Map<String, Permission> permissions = new PermissionMapper().ymlToPermissions(permissionsFile, null);

        return permissions.entrySet().stream()
            .map(Map.Entry::getValue)
            .filter(perm -> roleKey.equals(perm.getRoleKey()))
            .map(PermissionDTO::new)
            .collect(Collectors.toList());
    }

    /**
     * Update roles properties.
     * @param rolesYml roles config yml
     */
    private void updateRoles(String rolesYml) {
    }

    private void updatePermissions(String permissionsYml) {
    }

    /**
     * Get all roles.
     * @return roles set
     */
    public Set<RoleDTO> getAllRoles() throws IOException {
        return getRoles().entrySet().stream()
            .peek(entry -> entry.getValue().setKey(entry.getKey()))
            .map(Map.Entry::getValue)
            .map(RoleDTO::new)
            .collect(Collectors.toSet());
    }

    /**
     * Get role with permissions by role key.
     * @param roleKey the role key
     * @return roleDTO
     */
    public Optional<RoleDTO> getRole(String roleKey) throws IOException {
        Role role = getRoles().get(roleKey);
        if (role == null) {
            return Optional.empty();
        }
        role.setKey(roleKey);
        RoleDTO roleDto = new RoleDTO(role);
        roleDto.setPermissions(new TreeSet<>());

        // map key = MS_NAME:PRIVILEGE_KEY, value = PermissionDTO
        Map<String, PermissionDTO> permissions = new TreeMap<>();

        // create permissions dto with role permissions
        getPermissions().forEach((msName, rolePermissions) ->
            rolePermissions.entrySet().stream()
                .filter(entry -> roleKey.equalsIgnoreCase(entry.getKey()))
                .forEach(entry ->
                    entry.getValue().forEach(permission -> {
                        permission.setMsName(msName);
                        permission.setRoleKey(roleKey);
                        permissions.put(msName + ":" + permission.getPrivilegeKey(),
                            new PermissionDTO(permission));
                    })
                ));

        // enrich role permissions with missing privileges
        BiConsumer<String, Set<Privilege>> privilegesProcessor = (msName, privileges) ->
            privileges.forEach(privilege -> {
                PermissionDTO permission = permissions.get(msName + ":" + privilege.getKey());
                if (permission == null) {
                    permission = new PermissionDTO(msName, roleKey, privilege.getKey(), false);
                }
                permission.setResources(privilege.getResources());
                roleDto.getPermissions().add(permission);
            });

        getPrivileges().forEach(privilegesProcessor);
        roleDto.getPermissions().forEach(it -> it.setPermissionType(SYSTEM));
        Map<String, Set<Privilege>> customPrivileges = getCustomPrivileges();
        customPrivileges.forEach(privilegesProcessor);
        Set<String> customPrivilegeKeys = customPrivileges.values().stream().flatMap(Set::stream).map(Privilege::getKey)
            .collect(Collectors.toSet());
        roleDto.getPermissions().stream()
            .filter(it -> customPrivilegeKeys.contains(it.getPrivilegeKey())).forEach(it -> {
            if (it.getPermissionType() == SYSTEM) {
                //log.error("Custom privilege {} try to override system privilege, and ignored", it);
            } else {
                it.setPermissionType(TENANT);
            }
        });

        roleDto.setEnv(getEnvironments());

        return Optional.of(roleDto);
    }

    public List<String> getEnvironments() throws IOException {
        return getConfig(ENV_PATH, new TypeReference<List<String>>() {
        });
    }

    public void deleteRole(String roleKey) throws IOException {
        Map<String, Role> roles = getRoles();
        roles.remove(roleKey);
        updateRoles(mapper.writeValueAsString(roles));

        Map<String, Map<String, Set<Permission>>> permissions = getPermissions();
        for (Map<String, Set<Permission>> perm : permissions.values()) {
            perm.remove(roleKey);
        }
        updatePermissions(mapper.writeValueAsString(permissions));

    }

    /**
     * Get role matrix.
     * @return the role matrix
     */
    public RoleMatrixDTO getRoleMatrix() throws IOException {
        RoleMatrixDTO roleMatrix = new RoleMatrixDTO(getRoles().keySet());

        // map key = MS_NAME:PRIVILEGE_KEY, value = PermissionMatrixDTO
        Map<String, PermissionMatrixDTO> matrixPermissions = new HashMap<>();

        // create permissions matrix dto with role permissions
        getPermissions().forEach((msName, rolePermissions) ->
            rolePermissions.forEach((roleKey, permissions) ->
                    permissions.forEach(permission -> {
                        PermissionMatrixDTO permissionMatrix = matrixPermissions
                            .get(msName + ":" + permission.getPrivilegeKey());
                        if (permissionMatrix == null) {
                            permissionMatrix = new PermissionMatrixDTO(msName, permission.getPrivilegeKey());
                            matrixPermissions.put(msName + ":" + permission.getPrivilegeKey(), permissionMatrix);
                        }
                        if (Boolean.TRUE.equals(permission.getDisabled())) {
                            permissionMatrix.getRoles().add(roleKey);
                        }
                    })
                ));

        // enrich role permissions with missing privileges
        Consumer<Set<Privilege>> privilegesProcessor = privileges ->
            roleMatrix.getPermissions().addAll(privileges.stream().map(privilege -> {
                PermissionMatrixDTO permission = matrixPermissions
                    .get(privilege.getMsName() + ":" + privilege.getKey());
                if (permission == null) {
                    permission = new PermissionMatrixDTO(privilege.getMsName(), privilege.getKey());
                }
                return permission;
            }).collect(Collectors.toList()));


        getPrivileges().values().forEach(privilegesProcessor);
        roleMatrix.getPermissions().forEach(it -> it.setPermissionType(SYSTEM));
        Map<String, Set<Privilege>> customPrivileges = getCustomPrivileges();
        customPrivileges.values().forEach(privilegesProcessor);
        Set<String> customPrivilegeKeys = customPrivileges.values().stream().flatMap(Set::stream).map(Privilege::getKey)
            .collect(Collectors.toSet());
        roleMatrix.getPermissions().stream()
            .filter(it -> customPrivilegeKeys.contains(it.getPrivilegeKey())).forEach(it -> {
            if (it.getPermissionType() == SYSTEM) {
                //log.error("Custom privilege {} try to override system privilege, and ignored");
            } else {
                it.setPermissionType(TENANT);
            }
        });

        return roleMatrix;
    }

    public void updateRoleMatrix(RoleMatrixDTO roleMatrix) throws IOException {
        // create map key: MS_NAME:PRIVILEGE_KEY, value: PermissionMatrixDTO for easy search
        Map<String, PermissionMatrixDTO> newPermissions = new HashMap<>();
        for (PermissionMatrixDTO permission : roleMatrix.getPermissions()) {
            newPermissions.put(permission.getMsName() + ":" + permission.getPrivilegeKey(), permission);
        }

        Map<String, Map<String, Set<Permission>>> allPermissions = getPermissions();
        allPermissions.forEach((msName, rolePermissions) ->
            rolePermissions.entrySet().stream()
                // do not update hidden roles
                .filter(roleWithPermissions -> roleMatrix.getRoles().contains(roleWithPermissions.getKey()))
                // roleWithPermissions -> key: ROLE_KEY, value: set of role permissions
                .forEach(roleWithPermissions ->
                    roleWithPermissions.getValue().forEach(permission -> {
                        String key = msName + ":" + permission.getPrivilegeKey();
                        if (newPermissions.get(key) != null) {
                            /*
                             * disable permissions for current ROLE_KEY if it
                             * is not present in roleMatrix.permissions[].roles[] list
                             */
                            Set<String> roles = newPermissions.get(key).getRoles();
                            if (roles.contains(roleWithPermissions.getKey())) {
                                permission.setDisabled(false);
                                roles.remove(roleWithPermissions.getKey());
                            } else {
                                permission.setDisabled(true);
                            }
                        }
                    }))
        );

        // processing permissions for new role
        roleMatrix.getPermissions().stream().filter(permissionMatrixDTO ->
            !permissionMatrixDTO.getRoles().isEmpty()).forEach(permissionMatrixDTO -> {
                allPermissions.putIfAbsent(permissionMatrixDTO.getMsName(), new TreeMap<>());
                permissionMatrixDTO.getRoles().forEach(role -> {
                    allPermissions.get(permissionMatrixDTO.getMsName()).putIfAbsent(role, new TreeSet<>());
                    Permission permission = new Permission(
                            permissionMatrixDTO.getMsName(),
                            role,
                            permissionMatrixDTO.getPrivilegeKey());
                    permission.setDisabled(false);
                    allPermissions.get(permissionMatrixDTO.getMsName()).get(role).add(permission);
                });
            });
        updatePermissions(mapper.writeValueAsString(allPermissions));
    }

    /**
     * Set permissions based on role.
     * @param existingPermissions existing permissions
     * @param role the role that will be assigned with permissions
     * @param basedOn the role from where permissions will be received
     */
    private void enrichExistingPermissions(
                    Map<String, Map<String, Set<Permission>>> existingPermissions,
                    String role,
                    String basedOn) {
        for (Map<String, Set<Permission>> perm : existingPermissions.values()) {
            perm.put(role, perm.getOrDefault(basedOn, new TreeSet<>()));
        }
    }

    /**
     * Set permissions for role.
     * @param existingPermissions existing permissions
     * @param role the role that will be assigned with permissions
     */
    private void enrichExistingPermissions(
                    Map<String, Map<String, Set<Permission>>> existingPermissions,
                    String role) {
        for (Map<String, Set<Permission>> perm : existingPermissions.values()) {
            perm.put(role, new TreeSet<>());
        }
    }

    /**
     * Set permissions to role.
     * @param existingPermissions existing permissions
     * @param newPermissions permissions to add
     */
    private void enrichExistingPermissions(
                    Map<String, Map<String, Set<Permission>>> existingPermissions,
                    Collection<PermissionDTO> newPermissions) {
        newPermissions.forEach(permissionDto -> {
            existingPermissions.putIfAbsent(permissionDto.getMsName(), new TreeMap<>());
            existingPermissions.get(permissionDto.getMsName()).putIfAbsent(permissionDto.getRoleKey(),
                            new TreeSet<>());
            Permission permission = new PermissionDomainMapper().permissionDtoToPermission(permissionDto);
            existingPermissions.get(permissionDto.getMsName()).get(permissionDto.getRoleKey());
            // needed explicitly delete old permission
            existingPermissions.get(permissionDto.getMsName()).get(permissionDto.getRoleKey()).remove(permission);
            existingPermissions.get(permissionDto.getMsName()).get(permissionDto.getRoleKey()).add(permission);
        });
    }

    private <T> T getConfig(String configPath, TypeReference typeReference) throws IOException {
        String config = getConfigContent(configPath).orElse(EMPTY_YAML);
        return mapper.readValue(config, typeReference);
    }

    private Optional<String> getConfigContent(String configPath) {
        String tenant = "tenant name"; // TODO
        String config = null;
        config = "config content"; // TODO
        if (StringUtils.isBlank(config) || EMPTY_YAML.equals(config)) {
            config = null;
        }
        return Optional.empty();
    }
}

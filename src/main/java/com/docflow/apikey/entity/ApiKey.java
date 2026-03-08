package com.docflow.apikey.entity;

import com.docflow.audit.BaseEntity;
import com.docflow.project.entity.Project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "api_keys")
@Getter
@Setter
@NoArgsConstructor
public class ApiKey extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String name;

    @Column(name = "key_hash", nullable = false, unique = true)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 10)
    private String keyPrefix;

    @Column(nullable = false)
    private boolean revoked = false;

    public ApiKey(Project project, String name, String keyHash, String keyPrefix) {
        this.project = project;
        this.name = name;
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
    }
}

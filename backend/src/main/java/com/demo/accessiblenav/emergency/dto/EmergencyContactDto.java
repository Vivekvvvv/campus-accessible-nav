package com.demo.accessiblenav.emergency.dto;

import com.demo.accessiblenav.emergency.ContactRelationship;

import java.time.Instant;

/**
 * 紧急联系人DTO
 */
public class EmergencyContactDto {

    private Long id;
    private String contactName;
    private String phoneNumber;
    private String email;
    private ContactRelationship relationship;
    private String relationshipDisplay;
    private Boolean isPrimary;
    private Instant createdAt;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public ContactRelationship getRelationship() { return relationship; }
    public void setRelationship(ContactRelationship relationship) { this.relationship = relationship; }

    public String getRelationshipDisplay() { return relationshipDisplay; }
    public void setRelationshipDisplay(String relationshipDisplay) { this.relationshipDisplay = relationshipDisplay; }

    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

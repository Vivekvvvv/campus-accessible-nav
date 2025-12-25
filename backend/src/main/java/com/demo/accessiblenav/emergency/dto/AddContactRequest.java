package com.demo.accessiblenav.emergency.dto;

import com.demo.accessiblenav.emergency.ContactRelationship;

import jakarta.validation.constraints.NotBlank;

/**
 * 添加紧急联系人请求
 */
public class AddContactRequest {

    @NotBlank(message = "联系人姓名不能为空")
    private String contactName;

    @NotBlank(message = "联系人电话不能为空")
    private String phoneNumber;

    private String email;

    private ContactRelationship relationship;

    private Boolean isPrimary = false;

    // Getters and Setters
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public ContactRelationship getRelationship() { return relationship; }
    public void setRelationship(ContactRelationship relationship) { this.relationship = relationship; }

    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary; }
}

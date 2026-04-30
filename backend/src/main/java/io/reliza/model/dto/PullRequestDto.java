/**
* Copyright Reliza Incorporated. 2019 - 2026. All rights reserved.
*/

package io.reliza.model.dto;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.reliza.common.CommonVariables;
import io.reliza.common.CommonVariables.PullRequestState;
import io.reliza.common.DateDeserializer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PullRequestDto {
	@JsonProperty(CommonVariables.STATE_FIELD)
	private PullRequestState state;
    @JsonProperty(CommonVariables.COMPONENT_FIELD)
	private UUID component;
    @JsonProperty(CommonVariables.BRANCH_FIELD)
	private UUID branch;
    @JsonProperty(CommonVariables.TARGET_BRANCH_FIELD)
	private UUID targetBranch;
    @JsonProperty(CommonVariables.ENDPOINT_FIELD)
	private URI endpoint;
    @JsonProperty(CommonVariables.NUMBER_FIELD)
	private Integer number;
	
    @JsonProperty(CommonVariables.TITLE_FIELD)
	private String title;
	
    @JsonDeserialize(using = DateDeserializer.class)
	@JsonProperty(CommonVariables.CREATED_DATE_FIELD)
	private ZonedDateTime createdDate;
    @JsonDeserialize(using = DateDeserializer.class)
	@JsonProperty(CommonVariables.CLOSED_DATE_FIELD)
	private ZonedDateTime closedDate;
    @JsonDeserialize(using = DateDeserializer.class)
	@JsonProperty(CommonVariables.MERGED_DATE_FIELD)
	private ZonedDateTime mergedDate;

    @JsonProperty(CommonVariables.COMMITS_FIELD)
	private Set<UUID> commits;

}

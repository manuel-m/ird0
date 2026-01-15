package com.ird0.incident.mapper;

import com.ird0.incident.dto.CommentResponse;
import com.ird0.incident.dto.ExpertAssignmentResponse;
import com.ird0.incident.dto.IncidentEventResponse;
import com.ird0.incident.dto.IncidentResponse;
import com.ird0.incident.dto.IncidentSummaryResponse;
import com.ird0.incident.dto.LocationDTO;
import com.ird0.incident.model.Comment;
import com.ird0.incident.model.ExpertAssignment;
import com.ird0.incident.model.Incident;
import com.ird0.incident.model.IncidentEvent;
import com.ird0.incident.model.Location;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface IncidentMapper {

  IncidentResponse toResponse(Incident incident);

  IncidentSummaryResponse toSummaryResponse(Incident incident);

  List<IncidentSummaryResponse> toSummaryResponseList(List<Incident> incidents);

  ExpertAssignmentResponse toExpertAssignmentResponse(ExpertAssignment assignment);

  List<ExpertAssignmentResponse> toExpertAssignmentResponseList(List<ExpertAssignment> assignments);

  CommentResponse toCommentResponse(Comment comment);

  List<CommentResponse> toCommentResponseList(List<Comment> comments);

  IncidentEventResponse toEventResponse(IncidentEvent event);

  List<IncidentEventResponse> toEventResponseList(List<IncidentEvent> events);

  LocationDTO toLocationDTO(Location location);

  Location toLocation(LocationDTO dto);
}

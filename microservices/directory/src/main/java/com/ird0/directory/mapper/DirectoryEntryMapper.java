package com.ird0.directory.mapper;

import com.ird0.directory.dto.DirectoryEntryDTO;
import com.ird0.directory.model.DirectoryEntry;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface DirectoryEntryMapper {

  DirectoryEntryDTO toDTO(DirectoryEntry entity);

  DirectoryEntry toEntity(DirectoryEntryDTO dto);

  void updateEntityFromDTO(DirectoryEntryDTO dto, @MappingTarget DirectoryEntry entity);

  List<DirectoryEntryDTO> toDTOList(List<DirectoryEntry> entities);
}

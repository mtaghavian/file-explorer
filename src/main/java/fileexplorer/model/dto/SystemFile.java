package fileexplorer.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class SystemFile {

    private String name;

    private Long length;

    private Boolean isDirectory;
}

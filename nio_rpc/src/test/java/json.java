import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

public class json {

    @Test
    public void test() throws JsonProcessingException {
        Result res = new Result(Result.Status.DONE, "wow");
        res.setData("weoqwoe".getBytes());
        System.out.println(new ObjectMapper().writeValueAsString(res));
    }
}

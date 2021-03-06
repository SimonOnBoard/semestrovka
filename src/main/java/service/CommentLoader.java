package service;

import dao.CommentDaoImpl;
import dao.interfaces.CommentDao;
import dao.oldDaoWithoutInterfaces.UsersRepository;
import dto.UserDto;
import javafx.util.Pair;
import model.Comment;

import java.util.*;

public class CommentLoader {
    private UsersRepository usersRepository;
    private CommentDao commentDao;

    public CommentLoader() {
        this.usersRepository = new UsersRepository();
        this.commentDao = new CommentDaoImpl();
    }

    public List<Pair<Comment, UserDto>> getComments(Long id) {
        List<Comment> comments = commentDao.findAllByPostId(id);
        Set<Long> userIds = new HashSet<>();
        comments.forEach(comment -> userIds.add(comment.getOwnerId()));
        Map<Long, UserDto> dtos = usersRepository.findAllDtoBySet(userIds);
        List<Pair<Comment, UserDto>> info = new ArrayList<>();
        comments.forEach(comment -> info.add(new Pair<>(comment, dtos.get(comment.getOwnerId()))));
        return info;
    }

    public UserDto getUserDtoById(Long id) {
         Optional<UserDto> userDto = usersRepository.findDtoById(id);
         if(userDto.isPresent()){
             return userDto.get();
         }
         else{
             throw new IllegalArgumentException("Нету комментария");
         }
    }

    public void save(Long id, String text) {
        Optional<Comment> comment = commentDao.find(id);
        if(comment.isPresent()){
            Comment comment1 = comment.get();
            comment1.setText(text);
            commentDao.update(comment1);
        }
        else{
            throw new IllegalStateException("Пытаемся обновить комментарий которого нет");
        }

    }
}

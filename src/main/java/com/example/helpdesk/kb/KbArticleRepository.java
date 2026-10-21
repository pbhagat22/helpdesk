package com.example.helpdesk.kb;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbArticleRepository extends JpaRepository<KbArticle, Long> {

	@EntityGraph(attributePaths = "author")
	List<KbArticle> findAllByOrderByTitleAsc();

}

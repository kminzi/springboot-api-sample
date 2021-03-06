package com.study.springstudy.events;

import com.study.springstudy.accounts.Account;
import com.study.springstudy.accounts.AccountAdapter;
import com.study.springstudy.accounts.CurrentUser;
import com.study.springstudy.common.ErrorsResource;
import com.sun.org.apache.bcel.internal.generic.ACONST_NULL;
import lombok.var;
import org.modelmapper.ModelMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Controller;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.net.URI;
import java.util.Optional;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

@Controller
@RequestMapping(value="/api/events", produces= MediaTypes.HAL_JSON_VALUE)
public class EventController {

    private final EventRepository eventRepository;
    private final ModelMapper modelMapper;
    private final EventValidator eventValidator;

    @Autowired
    public EventController(EventRepository eventRepository, ModelMapper modelMapper, EventValidator eventValidator){
        this.eventRepository = eventRepository;
        this.modelMapper = modelMapper;
        this.eventValidator = eventValidator;
    }

    @PostMapping
    public ResponseEntity createEvent(@RequestBody @Valid EventDto eventDto, Errors errors,
                                      @CurrentUser Account account){

        //@valid ??? ?????? validation
        if(errors.hasErrors()){
            return badRequest(errors);
        }

        //custom validation
        eventValidator.validate(eventDto, errors);
        if(errors.hasErrors()){
            return badRequest(errors);
        }

        //EventDto -> Event(value limit ??? ??????)
        //??????1. Builder??? ????????? ?????? ??????
        Event event1 = Event.builder()
                .name(eventDto.getName())
                .description(eventDto.getDescription())
                .build();
        //??????2. ModelMapper ??????
        Event event = modelMapper.map(eventDto, Event.class);
        event.update();
        event.setManager(account);
        Event newEvent = this.eventRepository.save(event);

        //hateoas ????????? ?????? ?????? ??????
        WebMvcLinkBuilder selflinkBuilder = linkTo(EventController.class).slash(newEvent.getId());
        URI createdUri = selflinkBuilder.toUri();
        EventResource eventResource = new EventResource(event);
        eventResource.add(linkTo(EventController.class).withRel("query-events"));
        eventResource.add(selflinkBuilder.withRel("update-events")); //PUT method??? ???????????? ???
        eventResource.add(new Link("/docs/indexFile.html#resources-events-create").withRel("profile"));
        return ResponseEntity.created(createdUri).body(eventResource);
    }

    @GetMapping
    public ResponseEntity queryEvents(Pageable pageable,
                                      PagedResourcesAssembler<Event> assembler,
                                      @CurrentUser Account account){
//        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//
//        //accountService ?????? ????????? loadUserByUsername??? ????????? => authenticationPrincipal ??? ???????????? ?????? ??????
//        User principal = (User)authentication.getPrincipal();

        Page<Event> page = this.eventRepository.findAll(pageable);
        var pagedModel = assembler.toModel(page, e -> new EventResource(e));
        pagedModel.add(new Link("/docs/indexFile.html#resources-events-list").withRel("profile"));

        if(account != null){//auth??? ??????
            pagedModel.add(linkTo(EventController.class).withRel("create-event"));
        }

        return ResponseEntity.ok(pagedModel);
    }

    @GetMapping("/{id}")
    public ResponseEntity getEvent(@PathVariable Integer id,
                                   @CurrentUser Account account){
        Optional<Event> optionalEvent = this.eventRepository.findById(id);
        if(!optionalEvent.isPresent()){
            return ResponseEntity.notFound().build();
        }

        Event event = optionalEvent.get();
        EventResource eventResource = new EventResource(event);
        eventResource.add(new Link("/docs/indexFile.html#resources-events-get").withRel("profile"));
        if (event.getManager().equals(account)){
            eventResource.add(linkTo(EventController.class).slash(event.getId()).withRel("update-event"));
        }

        return ResponseEntity.ok(eventResource);
    }

    @PutMapping("/{id}")
    public ResponseEntity updateEvent(@PathVariable Integer id, @RequestBody @Valid EventDto eventDto,
                                      Errors errors, @CurrentUser Account account){
        //???????????? ?????? ???????????? ??????
        Optional<Event> optionalEvent = this.eventRepository.findById(id);
        if(!optionalEvent.isPresent()){
            return ResponseEntity.notFound().build();
        }

        //request body validation
        if(errors.hasErrors()){
            return badRequest(errors);
        }
        this.eventValidator.validate(eventDto, errors);
        if(errors.hasErrors()){
            return badRequest(errors);
        }

        Event existingEvent = optionalEvent.get();
        if(!existingEvent.getManager().equals(account)){
            return new ResponseEntity(HttpStatus.UNAUTHORIZED);
        }
        this.modelMapper.map(eventDto, existingEvent); //(?????? ?????????, ?????? ?????????)
        Event savedEvent = this.eventRepository.save(existingEvent);

        EventResource eventResource = new EventResource(savedEvent);
        eventResource.add(new Link("/docs/indexFile.html#resources-events-update").withRel("profile"));

        return ResponseEntity.ok(eventResource);
    }

    private ResponseEntity badRequest(Errors errors) {
        return ResponseEntity.badRequest().body(new ErrorsResource(errors));
    }
}

